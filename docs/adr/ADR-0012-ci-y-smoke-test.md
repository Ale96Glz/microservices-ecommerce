# ADR-0012: Pipeline CI y smoke tests E2E

- **Estatus:** Aceptado
- **Fecha:** 2026-09

## Contexto y Problema

El repositorio ya cuenta con:

- `ci.yml`: corre `mvn -B clean verify` (compilación + ~150 tests unitarios, de
  integración y de contrato) en cada push/PR.
- `release-images.yml`: publica las imágenes de los 6 microservicios + gateway a
  GHCR (`ghcr.io/ale96glz/microservices-ecommerce/<servicio>:<version>`) en cada
  tag `v*` o dispatch manual, y crea el Release.

Los tests unitarios/integración/contrato (ADRs previos, Fase 4) valida el código
dentro de cada módulo con H2/contexto Spring, pero **no** verifican el sistema
completo ya empaquetado:

- La configuración real de las imágenes Docker (env vars, outbox, listeners
  Kafka, wiring del gateway) no se ejecuta en CI.
- El flujo de negocio de punta a punta (registro → catálogo → pedido → pago →
  notificación) solo se ha probado de forma manual.
- No se verifica la trazabilidad distribuida (Zipkin) ni los tópicos Kafka.

La hoja de ruta pide "Automatizar smoke tests en CI". El `docker-compose.yml`
solo definía `build:`, de modo que no se podía levantar el stack a partir de las
imágenes publicadas en GHCR (siempre reconstruiría).

## Opciones Evaluadas

1. **Smoke sobre imágenes publicadas en GHCR** (`docker compose pull` +
   `up --no-build`):
   - Valida el **artefacto real** que se despliega (misma imagen que usan k8s).
   - Rápido (no compila en el runner) y fiel a producción.
   - Requiere ajustar el compose para poder consumir imágenes publicadas
     (añadir `image:` junto a `build:`) y que exista al menos una versión
     publicada.
   - Compose soporta esto: con `image` + `build`, `docker compose pull` baja la
     imagen `image:` y `up --no-build` la usa sin construir (confirmado en
     docs/issue #6464 y SO).
2. **Build en el runner + `up --build`**:
   - Más lento (6 builds multi-stage Maven veces el tiempo de CI) y valida el
     build local, no la imagen realmente publicada/desplegable.
   - Útil como verificación de que los Dockerfiles no rompen, pero el objetivo
     del smoke es validar lo publicado.
3. **Smoke en Kubernetes Kind dentro de GitHub Actions**:
   - Más fiel (Ingress, HTTPS, probes), pero Kind en runners es inestable y
     costoso; duplica infraestructura que el stack local (compose) ya
     representa para validar este nivel.

## Decisión

1. **`docker-compose.yml`**: cada microservicio agrega
   `image: ${IMAGE_PREFIX:-ghcr.io/ale96glz/microservices-ecommerce}/<servicio>:${IMAGE_VERSION:-latest}`
   junto a su `build:`. El desarrollo local sigue igual (`docker compose up
   --build`); CI/CD usa `docker compose pull` + `up --no-build` para consumir
   únicamente artefactos publicados (`IMAGE_VERSION` permite probar cualquier
   tag; el release a GHCR ya no publica `latest`).
2. **Workflow `smoke.yml`** (`.github/workflows`):
   - **Disparadores**: manual (`workflow_dispatch` con `version` obligatoria y
     `registry`) y automático tras `release-images` en verde
     (`workflow_run.completed`), resolviendo el tag `v*` del commit publicado.
   - Levanta **solo los servicios necesarios** del compose (postgres, kafka,
     zipkin, redis, los 6 microservicios y api-gateway); se excluyen las
     herramientas de UI/ops (kafka-ui, prometheus, grafana) para reducir tiempo
     y recursos.
   - Ejecuta `scripts/smoke-test.sh` y, en caso de fallo, vuelca los logs de
     todos los servicios. Siempre limpia con `down -v`.
   - `concurrency.group: smoke` evita corridas simultáneas que pisen los
     puertos.
3. **`scripts/smoke-test.sh`** recorre el escenario de compra real a través del
   gateway (`localhost:8080`, puerto único publicado según ADR-0007):

   1. **Registro** (`POST /api/v1/auth/register`, 201) y **login**
      (200, rol `USER`).
   2. **Bootstrap a ADMIN** vía `psql` en `auth_db` (no existe usuario admin
      inicial; solo afecta a la BD efímera del CI, que se destruye con
      `down -v`). Nuevo login → token con rol `ADMIN`.
   3. **Categoría** (201) y **producto** (201, `stock=50`).
   4. **Pedido** (201, 3 unidades) → verifica `stock=47` (reserva síncrona
      catálogo/pedidos) → publica `order-created` (outbox).
   5. **Pago** (201, `monto=total`) → publica `payment-processed`.
   6. **Estados posteriores a la cadena Kafka** (con polling): pedido pasa a
      `PAGADO` (lo marca el listener en pedidos) y el usuario tiene **al menos
      una notificación** (listeners en notificaciones).
   7. **Trazabilidad**: Zipkin (`/api/v2/traces`) contiene al menos una traza
      de la cadena gateway → pedidos → pagos → notificaciones.
4. **`ci.yml`**: además de `mvn verify`, sube los reportes surefire/failsafe
   como artefacto (`test-reports`) siempre que el job termine, para diagnóstico
   local de fallos.
5. **Primera corrida**: el smoke manual pide un tag concreto (p. ej. `1.1.9`);
   si no existe en GHCR, `docker compose pull` lo reporta.

## Consecuencias

### Positivas

- **El artefacto publicado es el que se prueba**: exactamente las mismas
  imágenes que se despliegan en k8s, mitigando "en mi máquina funciona".
- **Error-detection en la cadena completa**: la configuración real del stack
  (env vars, outbox, tópicos Kafka, gateway, Zipkin) queda cubierta y el
  escenario de compra queda documentado de forma ejecutable.
- **Reintentos por defecto**: smoke automático tras cada release avisa si una
  versión publicada no levanta o rompe el flujo.
- **Cero secretos**: el smoke no requiere credenciales (GHCR público, JWT de
  desarrollo); el `GITHUB_TOKEN` no es necesario.
- **Coste reducido**: no compila en el job de smoke; las imágenes ya están en
  GHCR (cache).

### Negativas / Compromisos

- **Imagine/red**: el stack completo (10 contenedores) puede tardar varios
  minutos en quedar listo en el runner; se mitiga con `depends_on` con health y
  polling en el script.
- **Bootstrap vía psql**: promover a ADMIN manipulando la BD es frágil ante
  cambios de esquema (tabla/columna `rol`) y queda desacoplado de la API; es
  aceptable porque la BD es efímera (down -v) y solo se usa como bootstrap
  para que el escenario de catálogo (solo admin) sea ejecutable.
- **Fragilidad de tiempos de Kafka**: los estados post-eventos dependen del
  selector/outbox; los timeouts (hasta 90s) y el polling mitigan flake de CI.
  El smoke expuso una carrera real: los consumidores arrancaban con
  `auto.offset.reset=latest` (default) sin offset commitado, por lo que se
  saltaban silenciosamente los eventos publicados antes de que el grupo
  terminara de unirse (pedidos pagados sin notificación, notificaciones vacías
  sin errores en el DLT). Se corrigió en **`pedidos-service`,
  `pagos-service` y `notificaciones-service`** fijando
  `ConsumerConfig.AUTO_OFFSET_RESET_CONFIG=earliest` (y en sus consumidores
  `.DLT`); los listeners son idempotentes ante reproceso (guardas de estado
  `CREADO`/`findByPedidoId`), por lo que es seguro re-procesar desde el inicio.
  Sin este ajuste el smoke FALLABA de forma intermitente incluso con el código
  actual.
- **No sustituye a los tests de niveles inferiores**: unitarios/integración/
  contrato siguen siendo el primer muro de regresión; el smoke es el último
  (más lento y costoso).
- **Duplicación de servicios en compose**: `image` + `build` conviven; si se
  cambia el nombre/estructura del registro hay que mantener las dos variantes.