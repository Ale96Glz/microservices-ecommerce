# ADR-0015: Arranque ordenado del API Gateway (gateway espera a auth y redis sanos)

- **Estatus:** Aceptado
- **Fecha:** 2026-09

## Contexto y Problema

En `docker-compose.yml` el `api-gateway` declaraba `depends_on` en su forma
plana (equivalente a `condition: service_started`) para auth-service,
catalogo-service, pedidos-service, pagos-service, notificaciones-service y
redis. El gateway arrancaba por tanto **en cuanto los contenedores existían**,
no cuando estaban operativos.

El smoke E2E (paso 1) considera "listo" al gateway cuando su propio
`/actuator/health` devuelve `200`. Ese health es **solo del gateway** y no
depende de los downstreams, así que podía ser `200` mientras auth-service,
notificaciones-service, etc. seguían arrancando (migrando esquema, conectando a
Kafka/PostgreSQL). El primer `POST /api/v1/auth/register`/`login` del smoke
chocaba entonces con el auth-service aún no operativo → `5xx`, y el script solo
lo absorbía reintentándolos hasta 120 s (`expect_retryable_srv`).

El síntoma visible: mensajes `retry: HTTP esperado 200, obtenido 503/502` al
inicio del smoke, es decir, el gateway aceptaba tráfico antes de tener a sus
dependencias listas.

Hechos verificados:
- El gateway usa `depends_on` plano (solo "started") en
  `docker-compose.yml`.
- `auth-service` **no tenía healthcheck** en el compose; expone
  `/actuator/health` en el puerto `8081` (endpoints `health,info,prometheus`
  con probes habilitadas).
- `redis` (necesario para el rate limiting del gateway, ADR-0010) **sí tenía**
  healthcheck, pero el gateway no esperaba `service_healthy`.
- Las imágenes son `eclipse-temurin:21-jre-alpine` (sin `curl`; busybox
  proporciona `wget`).

## Opciones Evaluadas

1. **Healthcheck en auth + `depends_on: condition: service_healthy` en el
   gateway (elegida)**:
   - Añadir a `auth-service` un healthcheck que consulte su
     `/actuator/health` con `wget` y cambiar el `depends_on` del gateway a un
     mapa con `auth-service` y `redis` en `service_healthy` (el resto se queda
     en `service_started`).
   - Pros: ataca la **causa raíz** (el gateway no arranca hasta que auth y
     redis estén operativos), el `200` del health del gateway pasa a implicar
     dependencias listas, es solo configuración de compose (riesgo bajo) y
     reduce la dependencia del smoke en sus reintentos.
   - Contras: los healthchecks dependen de busybox `wget` en las imágenes
     alpine (verificado disponible); añade latencia de arranque (el gateway
     espera); **no aplica a Kubernetes** (allí el orden no respeta compose; se
     gestiona con readiness, probes ya habilitadas en los servicios).
2. **Solo retry de 5xx en el smoke**: el script ya lo implementa
   (`expect_retryable_srv`), pero solo mitiga el síntoma y deja la carrera
   presente; no resuelve nada por sí sola.
3. **Readiness del gateway contra downstreams**: health agregado en el gateway
   que consulta a todos los servicios antes de reportarse sano. Exceso para el
   arranque local/CI y añade acoplamiento del gateway a los servicios.

## Decisión

1. **Healthcheck de `auth-service`** en el compose:
   ```yaml
   healthcheck:
     test: ["CMD-SHELL", "wget -q -O /dev/null http://localhost:8081/actuator/health || exit 1"]
     interval: 5s
     timeout: 5s
     retries: 10
   ```
2. **`depends_on` del gateway en forma de mapa**: `auth-service` y `redis` con
   `condition: service_healthy` (redis ya tenía healthcheck); catalogo, pedidos,
   pagos y notificaciones se mantienen en `service_started` (su curva de
   arranque ya la absorbe el smoke con reintentos, y el gateway no depende de
   ellos para arrancar, solo para rutear).
3. **El retry de 5xx del smoke se conserva** como red de seguridad (no se
   elimina).
4. **Contratos e integración no cambian**: la validación es el propio smoke E2E
   (paso 1 verde sin dependencia de reintentos de arranque y pasos
   `register`/`login` sin `5xx` por carrera).

## Consecuencias

### Positivas

- **Arranque determinista en Compose/CI**: el gateway acepta tráfico solo
  cuando auth y redis están sanos; el health del gateway es una señal fiable de
  operatividad del flujo de auth.
- **Smoke menos ruidoso**: desaparecen (o se reduce drásticamente) los
  reintentos por `5xx` de arranque; el CI es más estable y legible.
- **Cero cambios de código**: solo configuración de infraestructura.

### Negativas / Compromisos

- **Latencia de arranque**: el gateway espera a que auth pase el healthcheck
  (≈ 5-10 s adicionales sobre el arranque de auth). Aceptable en local/CI.
- **Dependencia de busybox `wget`**: si una futura base de imagen la retirara,
  el healthcheck fallaría; se mitiga verificando la imagen al hacer cambios.
- **No aplica a Kubernetes**: el orden de arranque real en producción se sigue
  gestionando con readiness/liveness (probes ya activas en los servicios);
  este ADR cubre el arranque local/CI de Compose.
- **Sin healthcheck para el resto de servicios**: catalogo/pedidos/pagos/
  notificaciones se quedan en `service_started`; su arranque lento se absorbe
  con los reintentos del smoke (no bloquean el arranque del gateway).