# Ecommerce Microservices

Aplicación de comercio electrónico construida como un monorepo Maven con
microservicios independientes. El proyecto permite gestionar usuarios,
catálogo, pedidos, pagos y notificaciones mediante APIs REST.

## Estado actual

### Servicios

- `api-gateway` — punto de entrada HTTP en el puerto `8080`, rutas hacia los
  servicios y validación de JWT.
- `auth-service` — registro, login, JWT y gestión de usuarios. Puerto `8081`.
- `catalogo-service` — gestión de productos y categorías, incluyendo stock,
  precios y estados. Puerto `8082`.
- `pedidos-service` — creación, consulta y cancelación de pedidos. Obtiene la
  información de los productos desde catálogo. Puerto `8083`.
- `pagos-service` — procesamiento de pagos, consultas e idempotencia por
  pedido. Puerto `8084`.
- `notificaciones-service` — registro, consulta y marcado de notificaciones.
  Puerto `8085`.
- `common-events` — eventos compartidos entre servicios.

### Funcionalidades implementadas

- APIs REST bajo `/api/v1`.
- Validación de datos de entrada.
- DTOs, mappers, repositorios y servicios separados por módulo.
- Manejo centralizado de errores HTTP.
- Contraseñas almacenadas con BCrypt.
- Autenticación mediante JWT.
- Autorización básica por usuario y rol `ADMIN`.
- Acceso a recursos propios para pedidos, pagos y notificaciones.
- Paginación de listados.
- Swagger/OpenAPI en los servicios REST.
- H2 para ejecución local.
- PostgreSQL para ejecución con Docker.
- Kafka para la comunicación entre pedidos, pagos, notificaciones y catálogo.
- Redis para rate limiting distribuido en el gateway, sesiones y caché.
- Kafka UI (Kafka UI / Topics) en `http://localhost:8089`.
- Observabilidad: trazabilidad distribuida con Zipkin, métricas con Prometheus y
  dashboards con Grafana (stack completo en Compose y Kubernetes).
- Ciclo de estados de pedidos (CREADO → PAGADO / CANCELADO) y pagos (PROCESADO /
  RECHAZADO con `motivoRechazo` en la respuesta), con simulación de rechazo y
  compensación de stock vía saga (outbox `RESTOCK_REQUIRED` → catálogo) al
  cancelar o rechazar.
- Reintento de pago rechazado (ADR-0016): intentos por pedido + reactivación
  explícita del pedido cancelado, con re-reserva atómica de stock.
- Transactional Outbox: los eventos (OrderCreated, PaymentProcessed,
  RestockRequested) se guardan en una tabla interna en la misma transacción del
  dato de negocio y un publicador los envía a Kafka.
- Reintentos con backoff y Dead Letter Topics (DLT) para eventos Kafka fallidos.
- Health checks compatibles con Kubernetes.
- Arranque orquestado en Compose: el gateway espera a auth-service y redis sanos (healthchecks) antes de aceptar tráfico (ADR-0015).

## Arquitectura de eventos

Con Kafka habilitado, el flujo principal es:

```text
Pedido creado
    ├── guarda Pedido + OutboxEvent (misma transacción)
    └── Outbox publisher publica OrderCreatedEvent
            └── pagos-service procesa el pago
                    ├── guarda Pago + OutboxEvent (misma transacción)
                    └── Outbox publisher publica PaymentProcessedEvent
                            └── notificaciones-service registra el aviso
```

Para garantizar consistencia entre la base de datos y Kafka, cada servicio productor
guarda un evento en una tabla `outbox_event` dentro de la misma transacción que
persiste el dato de negocio. Un publicador programado (`@Scheduled`) lee los eventos
pendientes, los publica en Kafka **esperando el ack del broker** y los marca como
`PUBLICADO`. Si el envío falla o Kafka no confirma, el evento permanece `PENDIENTE`
y se reintenta en el siguiente ciclo.

Los consumidores (pagos y notificaciones) reintentan los eventos fallidos con
backoff fijo (3 intentos por defecto) y, al agotarlos, publican el mensaje en
un Dead Letter Topic (`<topico>.DLT`) para su diagnóstico sin bloquear la
partición original.

En ejecución local Kafka está deshabilitado por defecto para facilitar el
desarrollo. En Docker Compose se habilita automáticamente.

## Requisitos

- Java 21
- Maven 3.9+
- Docker Desktop (para PostgreSQL, Kafka y el despliegue completo)

## Ejecución local

Para compilar todos los módulos:

```bash
mvn clean verify
```

Los servicios pueden ejecutarse individualmente con sus JAR generados:

```bash
java -jar auth-service/target/auth-service-0.0.1-SNAPSHOT.jar
java -jar catalogo-service/target/catalogo-service-0.0.1-SNAPSHOT.jar
java -jar pedidos-service/target/pedidos-service-0.0.1-SNAPSHOT.jar
java -jar pagos-service/target/pagos-service-0.0.1-SNAPSHOT.jar
java -jar notificaciones-service/target/notificaciones-service-0.0.1-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar
```

En local se utilizan bases de datos H2 independientes por servicio.

## Ejecución con Docker Compose

```bash
cp .env.example .env
docker compose up -d --build
```

El stack incluye:

- PostgreSQL con una base de datos por servicio.
- Redis (ratio limit, sesiones y caché).
- Kafka.
- Kafka UI.
- Zipkin (trazas).
- Prometheus (métricas).
- Grafana (dashboards).
- Los seis servicios de la aplicación.

| Recurso | URL |
|---|---|
| API Gateway (única entrada) | http://localhost:8080 |
| Auth | interno (red de Compose, `auth-service:8081`) |
| Catálogo | interno (red de Compose, `catalogo-service:8082`) |
| Pedidos | interno (red de Compose, `pedidos-service:8083`) |
| Pagos | interno (red de Compose, `pagos-service:8084`) |
| Notificaciones | interno (red de Compose, `notificaciones-service:8085`) |
| Kafka UI | http://localhost:8089 |
| Zipkin | http://localhost:9411 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |

Para detener el entorno:

```bash
docker compose down
```

Para utilizar imágenes publicadas en GitHub Container Registry:

```bash
set IMAGE_TAG=1.0.0
docker compose -f docker-compose.yml -f docker-compose.ghcr.yml pull
docker compose -f docker-compose.yml -f docker-compose.ghcr.yml up -d --no-build
```

## Despliegue inicial en Kubernetes

El script interactivo multiplataforma `scripts/deploy-k8s.ps1` aplica la
infraestructura por etapas y solicita las credenciales sin mostrarlas en
pantalla. Requiere PowerShell 5.1 en Windows o PowerShell 7 en Linux/macOS.

En Windows:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\deploy-k8s.ps1
```

En Linux o macOS:

```bash
pwsh -File ./scripts/deploy-k8s.ps1
```

El stack de aplicación (microservicios + Ingress) está en
`scripts/deploy-k8s-full.ps1`. Por defecto usa el último tag git `v*` como
versión de imagen en GHCR (`v1.1.1` → `:1.1.1`), sin dejar `:latest` en los
manifiestos:

```powershell
.\scripts\deploy-k8s-full.ps1
.\scripts\deploy-k8s-full.ps1 -ImageVersion 1.1.1
```

Actualmente ejecuta:

1. Validación del contexto y los nodos.
2. Creación del namespace `ecommerce`.
3. Aplicación del `ConfigMap`.
4. Aplicación del `SealedSecret` versionado y verificación del Secret real.
5. Despliegue de PostgreSQL y su volumen persistente.
6. Verificación de las cinco bases de datos.

Los secretos (`POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET`) están
**cifrados con Kubernetes Sealed Secrets** (ADR-0009): el archivo
`k8s/sealed-ecommerce-secrets.yaml` se versiona en git y el controller
`sealed-secrets` (namespace `kube-system`) lo descifra al aplicar. Las
credenciales nunca viajan en claro por el repositorio.

La **primera vez** de un clúster (o tras borrarlo) hay que instalar el
controller antes de aplicar el SealedSecret:

```bash
kubectl apply -f https://github.com/bitnami/sealed-secrets/releases/download/v0.40.0/controller.yaml
```

- Para **regenerar/rotar** el SealedSecret tras actualizar el Secret en el
  cluster: `./scripts/seal-ecommerce-secrets.ps1`.
- Si `ecommerce-secrets` no existe o fue borrado, el controller lo recrea desde
  el SealedSecret automáticamente.
- El binario `kubeseal` (solo para el operador, no se versiona) va en
  `tools/kubeseal/`; si falta, `seal-ecommerce-secrets.ps1` lo reclama.

## Autenticación y flujo básico

1. Registrar un usuario en `POST /api/v1/auth/register`.
2. Iniciar sesión en `POST /api/v1/auth/login`.
3. Enviar el token recibido en cada solicitud protegida:

```http
Authorization: Bearer <token>
```

El gateway deja públicos únicamente el registro y el login. Las operaciones
administrativas requieren el rol `ADMIN`.

Usuario administrador de demostración:

```text
Email: admin@ecommerce.local
Password: Admin1234
```

## Documentación de arquitectura

El registro de decisiones (ADRs) y los diagramas C4 (Contexto, Contenedores y
Componentes) están disponibles en [`docs/`](./docs/adr/README.md).

## Documentación de API

La documentación Swagger está disponible en cada servicio, pero en el entorno
por defecto **solo el API Gateway publica puerto al host**. Para ver el Swagger
de un microservicio interno hay que exponerlo puntualmente:

```bash
# Ejemplo: Swagger de catálogo
kubectl port-forward svc/catalogo-service -n ecommerce 8082:8080
```

```text
http://localhost:8082/swagger-ui.html
```

En Docker Compose local se puede habilitar temporalmente el puerto
(`CATALOGO_SERVICE_PORT=8082`) o entrar al contenedor:

```bash
docker exec -it ecommerce-catalogo sh
```

## HTTPS (TLS por defecto en el Ingress)

El Ingress termina TLS mediante un Secret `ecommerce-tls` creado con
`scripts/gen-tls.ps1` (CA propia + certificado para `ecommerce.local`, ver
ADR-0008). Para regenerar el certificado:

```powershell
.\scripts\gen-tls.ps1 ecommerce.local
```

El certificado/CA no se versiona en git (`certs/` ignorado); solo el script.

Para consumir por HTTPS desde el navegador sin advertencias:

1. Agregar `ecommerce.local` al archivo `hosts` apuntando a la IP del
   ingress-nginx:

   ```text
   # C:\Windows\System32\drivers\etc\hosts
   <IP-del-LoadBalancer>  ecommerce.local
   ```

   En la mayoría de entornos Docker Desktop esa IP se obtiene con:

   ```bash
   kubectl get svc -n ingress-nginx ingress-nginx-controller -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
   ```

2. Confiar en la CA de desarrollo en el navegador (importar
   `certs/ca-ecommerce.crt`).

3. Acceder:

   ```text
   https://ecommerce.local
   ```

Para pruebas por línea de comandos sin modificar el `hosts`:

```bash
curl --cacert certs/ca-ecommerce.crt --resolve ecommerce.local:443:<IP> https://ecommerce.local/actuator/health
```

> Nota: en algunos entornos (Docker Desktop/WSL2) el `LoadBalancer` no es
> alcanzable directo desde el host; usar `kubectl port-forward
> svc/ingress-nginx-controller -n ingress-nginx 8443:443` y apuntar
> `ecommerce.local` a `127.0.0.1` en el `hosts`.

## Health checks

Cada servicio expone endpoints de salud para el orquestador:

```text
http://localhost:<puerto>/actuator/health
http://localhost:<puerto>/actuator/health/liveness
http://localhost:<puerto>/actuator/health/readiness
```

Estos endpoints son usados por las `startupProbe`, `readinessProbe` y
`livenessProbe` de Kubernetes. En Kubernetes se consultan a través del
Service interno correspondiente (no están publicados al host).

## Smoke test E2E

El flujo completo de compra se automatiza en CI (`.github/workflows/smoke.yml`),
probando las imágenes publicadas en GHCR. Para ejecutarlo localmente con las
imágenes publicadas:

```bash
docker compose pull postgres kafka zipkin redis \
  auth-service catalogo-service pedidos-service pagos-service \
  notificaciones-service api-gateway
IMAGE_PREFIX=ghcr.io/ale96glz/microservices-ecommerce IMAGE_VERSION=latest \
  docker compose up -d --no-build postgres kafka zipkin redis \
  auth-service catalogo-service pedidos-service pagos-service \
  notificaciones-service api-gateway
./scripts/smoke-test.sh
```

El script recorre: registro → login → (bootstrap a `ADMIN` vía `psql`, no hay
admin inicial) → categoría → producto → pedido (descuenta stock, publica
`order-created`) → pago automático `PROCESADO` (`payment-processed`) → pedido
`PAGADO` → notificación creada → traza en Zipkin. Además valida la **saga de
compensación**: un segundo pedido con total superior al umbral de aprobación
(`100.00`) se rechaza automáticamente (`RECHAZADO`), el pedido pasa a `CANCELADO`
y la compensación por outbox (`restock-requested`) restaura el stock en catálogo.
La notificación del pago rechazado incluye el motivo (`motivoRechazo`); el
`POST /api/v1/pago` se usa en el reintento (pedido reactivado, sin
`order-created`) y responde `201` cuando el intento se registra, con el
resultado en el cuerpo. Desde el arranque, el
gateway solo acepta tráfico cuando auth-service y redis están sanos
(healthchecks en Compose), evitando la carrera de arranque. Los escenarios
completos (compra aprobada, rechazo + compensación, cancelación manual y
códigos de error) están documentados paso a paso en
[`docs/escenarios-de-compra.md`](./docs/escenarios-de-compra.md). El veredicto
de madurez y el plan de endurecimiento (secretos, CORS, migraciones, TLS,
backups) están en [`docs/produccion.md`](./docs/produccion.md). Detalles de
la decisión en
[ADR-0012](./docs/adr/ADR-0012-ci-y-smoke-test.md),
[ADR-0013](./docs/adr/ADR-0013-compensacion-stock-saga-outbox.md),
[ADR-0014](./docs/adr/ADR-0014-contrato-http-pago-201-motivechazo.md) y
[ADR-0015](./docs/adr/ADR-0015-arranque-ordenado-gateway-auth.md).

## Hoja de ruta

### Fase 1 — Plataforma y despliegue

- [x] Crear monorepo Maven y módulos independientes.
- [x] Implementar APIs REST principales.
- [x] Agregar gateway y autenticación JWT.
- [x] Preparar Dockerfiles y Docker Compose.
- [x] Integrar PostgreSQL, Kafka y Kafka UI para ejecución con Docker.
- [x] Automatizar compilación y publicación de imágenes en GHCR.
- [x] Crear la base de Kubernetes: namespace, ConfigMap y plantilla de Secret.
- [x] Agregar manifiestos para PostgreSQL, Kafka y los microservicios.
- [x] Configurar Secrets, Services e Ingress.
- [x] Agregar health checks y observabilidad (startup, readiness y liveness).

### Fase 2 — Robustez del negocio

- [x] Implementar reserva y actualización de stock (descuento atómico al crear el pedido).
- [x] Validar la existencia y el estado del usuario desde pedidos.
- [x] Mejorar el ciclo de estados de pedidos y pagos.
- [x] Agregar reintentos y manejo de errores para eventos Kafka (backoff + Dead Letter Topics).
- [x] Implementar Transactional Outbox.

### Fase 3 — Seguridad y operación

- [x] Validar JWT también dentro de cada microservicio (ADR-0006, módulo `common-security`).
- [x] Evitar la exposición directa de los puertos internos (ADR-0007).
- [x] Configurar HTTPS (ADR-0008, TLS self-signed por Ingress).
- [x] Gestión segura de secretos (ADR-0009, Kubernetes Sealed Secrets).
- [x] Agregar rate limiting (ADR-0010, RedisRateLimiter en el API Gateway).
- [x] Incorporar logs estructurados, métricas y trazabilidad.

### Fase 4 — Calidad

- [x] Agregar pruebas unitarias y de integración.
- [x] Añadir pruebas de contrato entre servicios.
- [x] Automatizar smoke tests en CI.
- [x] Documentar escenarios completos de compra.
- [x] Reintento de pago rechazado: [ADR-0016](./docs/adr/ADR-0016-reintento-pago-rechazado.md) (intentos por pedido + reactivación explícita) — implementado y verificado por smoke E2E.
