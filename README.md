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
- Kafka para la comunicación entre pedidos, pagos y notificaciones.
- Health checks compatibles con Kubernetes.

## Arquitectura de eventos

Con Kafka habilitado, el flujo principal es:

```text
Pedido creado
    └── OrderCreatedEvent
            └── pagos-service procesa el pago
                    └── PaymentProcessedEvent
                            └── notificaciones-service registra el aviso
```

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
- Kafka.
- Kafka UI.
- Los seis servicios de la aplicación.

URLs principales:

| Recurso | URL |
|---|---|
| API Gateway | http://localhost:8080 |
| Auth | http://localhost:8081 |
| Catálogo | http://localhost:8082 |
| Pedidos | http://localhost:8083 |
| Pagos | http://localhost:8084 |
| Notificaciones | http://localhost:8085 |
| Kafka UI | http://localhost:8089 |

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

Actualmente ejecuta:

1. Validación del contexto y los nodos.
2. Creación del namespace `ecommerce`.
3. Aplicación del `ConfigMap`.
4. Creación del `Secret` si todavía no existe.
5. Despliegue de PostgreSQL y su volumen persistente.
6. Verificación de las cinco bases de datos.

Si `ecommerce-secrets` ya existe, el script lo conserva para no cambiar
accidentalmente la contraseña de una base de datos existente.

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

## Documentación de API

La documentación Swagger está disponible en:

```text
http://localhost:<puerto>/swagger-ui.html
```

Por ejemplo:

```text
http://localhost:8082/swagger-ui.html
```

## Health checks

Cada servicio expone endpoints de salud para el orquestador:

```text
http://localhost:<puerto>/actuator/health
http://localhost:<puerto>/actuator/health/liveness
http://localhost:<puerto>/actuator/health/readiness
```

Estos endpoints serán utilizados más adelante por las `readinessProbe` y
`livenessProbe` de Kubernetes.

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

- [ ] Implementar reserva y actualización de stock.
- [ ] Validar la existencia y el estado del usuario desde pedidos.
- [ ] Mejorar el ciclo de estados de pedidos y pagos.
- [ ] Agregar reintentos y manejo de errores para eventos Kafka.
- [ ] Implementar Transactional Outbox.

### Fase 3 — Seguridad y operación

- [ ] Validar JWT también dentro de cada microservicio.
- [ ] Evitar la exposición directa de los puertos internos.
- [ ] Configurar HTTPS y gestión segura de secretos.
- [ ] Agregar rate limiting.
- [ ] Incorporar logs estructurados, métricas y trazabilidad.

### Fase 4 — Calidad

- [ ] Agregar pruebas unitarias y de integración.
- [ ] Añadir pruebas de contrato entre servicios.
- [ ] Automatizar smoke tests en CI.
- [ ] Documentar escenarios completos de compra.
