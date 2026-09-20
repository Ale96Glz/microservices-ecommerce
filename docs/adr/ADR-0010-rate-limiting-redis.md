# ADR-0010: Rate limiting distribuido con Redis en el API Gateway

- **Estatus:** Aceptado
- **Fecha:** 2026-09

## Contexto y Problema

El único punto de entrada al sistema es el `api-gateway` (Spring Cloud
Gateway, reactivo) que enruta a los cinco microservicios (ADR-0005, ADR-0007).
Hoy **no hay ningún límite de peticiones**: cualquier cliente puede saturar el
gateway o abusar de endpoints públicos (`POST /api/v1/auth/login`,
`POST /api/v1/auth/register`, GET de catálogo).

La Agenda Fase 3 exige agregar rate limiting. Se necesita una solución que:

- Limite peticiones de forma **distribuida** (el gateway puede tener más de
  una réplica en Kubernetes).
- Diferencie entre rutas (login/register público y sensible vs. el resto).
- Sea **configurable sin recompilar** (por variables de entorno).
- No agregue acoplamiento a los microservicios (se resuelve en el borde).

## Opciones Evaluadas

1. **RedisRateLimiter de Spring Cloud Gateway (filtro `RequestRateLimiter`)**:
   implementación oficial y reactiva que cuenta peticiones en **Redis** con un
   algoritmo de token bucket. El contador es compartido entre todas las réplicas
   del gateway. Se configura por ruta y el `KeyResolver` define qué identifica a
   un cliente (IP por defecto, JWT por usuario, etc.).
   - **Requiere**: un Redis nuevo en el stack (docker-compose + k8s) y las
     dependencias `spring-boot-starter-data-redis-reactive`.
   - El gateway ya es reactivo, así que encaja sin hilos extra.
2. **Bucket4j en memoria (circuit reservado por réplica o cache local)**:
   - Simple y sin infraestructura adicional, pero el contador vive en el proceso:
     con N réplicas el límite efectivo se multiplica por N, y se resetea en cada
     reinicio/rollout. No cumple el requisito de ser distribuido.
3. **Anotaciones del Ingress `nginx.ingress.kubernetes.io/limit-rps`**:
   - Sin código y en el edge, pero limita **por IP del cliente**, mezclando
     todos los usuarios detrás de un NAT/proxy; no distingue usuario ni ruta, y
     la semántica depende del ingress-controller, no del servicio API.

## Decisión

1. **Adoptar RedisRateLimiter en el api-gateway** (opción 1):

   - Se agrega **Redis** al entorno:
     - `docker-compose.yml`: servicio `redis` (puerto mapeado a 127.0.0.1:6379
       en lab; password opcional via `REDIS_PASSWORD`).
     - `k8s/`: `redis-deployment.yaml` + `redis-service.yaml` (ClusterIP,
       replicas=1). En Kubernetes Redis arranca con `--requirepass` y el
       password vive en `ecommerce-secrets` (`REDIS_PASSWORD`).
     - Config de conexión por env: `SPRING_DATA_REDIS_HOST`,
       `SPRING_DATA_REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD`.
   - **Dependencias** en `api-gateway/pom.xml`:
     `spring-boot-starter-data-redis-reactive`.
   - **Configuración por ruta** en `application.yml` usando el filtro
     `RequestRateLimiter`:
     - Rutas públicas sensibles (`/api/v1/auth/login`, `/api/v1/auth/register`):
       límite más **estricto** (ej. 10 rpm) para mitigar fuerza bruta.
     - Resto de rutas: límite por defecto (ej. 60 rpm o 100 rpm).
     - Valores parametrizados por env:
       `GATEWAY_RATE_LIMIT_PERMITS_PER_SECOND`,
       `GATEWAY_RATE_LIMIT_BURST_CAPACITY`, `...LOGIN_*` para la variante login.
   - **KeyResolver**: por **IP del cliente** (X-Forwarded-For del gateway,
     configurado para confiar en el Ingress). La identificación por JWT/usuario
     queda como mejora futura documentada.
   - El Redis **no guarda datos de negocio**: solo el estado del token bucket.
     Si cae, se evalúa degradación (ver Consecuencias).

2. **Respuesta de rechazo**: se mantiene el comportamiento por defecto del
   `RequestRateLimiter` (HTTP 429 Too Many Requests). No se expone en claro; el
   gateway lo devuelve con código 429.

3. **No se usan** Bucket4j en memoria ni anotaciones del Ingress (opciones 2/3)
   por los motivos expuestos; el Ingress se mantiene solo para TLS y enrutado.

## Consecuencias

### Positivas

- **Límite distribuido y consistente**: el contador vive en Redis, compartido
  por todas las réplicas del gateway; el límite es real y no depende de una sola
  instancia.
- **Protección del borde**: login/register quedan limitados más estrictamente,
  mitigando intentos de fuerza bruta primarios ya en el gateway (defensa
  adicional al JWT del ADR-0006).
- **Transparente a los microservicios**: no se toca a los 5 servicios; solo se
  configura el gateway.
- **Configurable por entorno** (env vars) sin recompilar.

### Negativas / Compromisos

- **Nueva infraestructura (Redis)**: hay que operarla en docker-compose y k8s,
  y perdura como componente del stack. Consume memoria (pequeña en dev).
- **Redis es un punto de falla del rate limiter**: Spring Cloud Gateway 4.1
  `RedisRateLimiter` falla **abierto** (permite si Redis no responde). En
  laboratorio eso se mantiene. En profile **`prod`**, `RedisFailClosedFilter`
  sondea Redis y responde **503** al API si no hay conexión (fail-closed);
  `/actuator` queda fuera para probes.
- **Identificación imperfecta**: el KeyResolver por IP agrupa a todos los
  usuarios del mismo NAT/proxy; la granularidad por JWT/usuario queda pendiente.
- **Redis en lab puede ir sin password**; en Kubernetes el password es
  obligatorio (`REDIS_PASSWORD` + `ProductionSecrets` en el gateway).