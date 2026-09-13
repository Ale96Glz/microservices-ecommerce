# ADR-0006: Validación JWT dentro de cada microservicio (defensa en profundidad)

- **Estatus:** Aceptado
- **Fecha:** 2026-09

## Contexto y Problema

El [ADR-0002](./ADR-0002-gateway-jwt-centralizado.md) centralizó la autenticación en
el api-gateway mediante el patrón *trusted-headers*: el gateway valida el JWT,
inyecta `X-User-Id`, `X-User-Email` y `X-User-Rol`, y cada microservicio confía
en esos headers a través del helper `GatewayAuth`. Los servicios no validan el
token y habilitan `anyRequest().permitAll()` (Spring Security).

La consecuencia negativa documentada en ADR-0002 era la **defensa en profundidad
limitada**: si un microservicio quedara expuesto directamente (misconfiguration
de red, puerto abierto, bypass del Ingress), cualquiera puede fabricar los
headers `X-User-Id` / `X-User-Rol` y obtener un `403` esquivado, escalando a
operaciones de `ADMIN` o a recursos ajenos.

Además existen llamadas internas síncronas que hoy viajan **sin token**:
`pedidos-service` invoca a `catalogo-service` (`/stock`, `/stock/reponer`) y a
`auth-service` (`/usuario/existe/{id}`) enviando únicamente `X-User-Id`, lo que
reproduce la misma debilidad en las rutas internas.

## Opciones Evaluadas

1. **Solo bloquear los puertos internos** (red): evita el acceso directo pero no
   defiende dentro del clúster (p. ej. un pod comprometido, DNS rebinding o un
   Service accidentalmente expuesto). No valida las llamadas internas.
2. **Validar JWT en cada microservicio como segunda capa** con el mismo
   `JWT_SECRET` compartido: cada servicio valida firma y expiración, y construye
   la identidad del usuario desde los *claims*, ignorando los headers entrantes.
3. **Tokens con claims de confianza distinta para servicio a servicio**
   (JWT específicos por servicio + roles técnicos): más robusto frente a un
   `JWT_SECRET` comprometido, pero agrega emisor de tokens interno, rotación y
   gestión de audiencias; sobrecoste alto para el alcance actual.

## Decisión

Se extiende el ADR-0002 **sin reemplazarlo**: el gateway sigue siendo la única
capa que autentica a clientes externos y propaga la identidad. A partir de ahora:

1. **Cada microservicio valida el JWT** en un filtro servlet propio
   (`JwtAuthFilter extends OncePerRequestFilter`) con JJWT 0.12.6 y el mismo
   `JWT_SECRET` compartido (gestionado como Secret en Kubernetes).
2. El filtro **ignora los headers `X-User-*` entrantes** y reconstruye la
   identidad (id, email, rol) desde los *claims* validados, envolviendo la
   petición para que `GatewayAuth` siga operando sin cambios en los controllers.
3. Si la ruta es protegida y no hay token (`Authorization: Bearer`) o el token
   es inválido/expirado → `401` directamente en el servicio.
4. **Rutas públicas mínimas** por filtro: `/actuator/**`, `/v3/api-docs/**`,
   `/swagger-ui/**` y `GET` de `/api/v1/producto/**` y `/api/v1/categoria/**`
   (solo en `catalogo-service`), alineadas con las públicas del gateway.
5. **Propagación del token en llamadas internas (síncronas)**: `pedidos-service`
   reenvía el `Authorization` original (obtenido del contexto de la petición
   actual) en sus llamadas REST a `catalogo-service` y `auth-service`, mediante
   un interceptor en el `RestClient.Builder`. Así las rutas internas también
   exigen token válido.
6. El filtro se habilita con `@ConditionalOnProperty(jwt.filter.enabled)` con
   valor por defecto `true`, replicando la política del gateway
   (`gateway.security.enabled`). Para ejecutar servicios sueltos en local sin
   gateway, se desactiva por variable de entorno.

El token es firmado por `auth-service`, compartido con el gateway y con todos
los microservicios mediante el `JWT_SECRET` del Secret `ecommerce-secrets`.

## Implementación (extracción a módulo común)

Para evitar la duplicación de código en cinco servicios, la implementación se
centralizó en un **módulo compartido `common-security`** (mismo patrón que el ya
existente `common-events`):

- Paquete `com.aosorio.ecommerce.security` con `JwtValidator`, `JwtAuthFilter`
  y `JwtIdentityRequestWrapper` (una sola copia).
- Se registra mediante **Spring Boot AutoConfiguration**
  (`JwtSecurityAutoConfiguration` + `META-INF/spring/*.imports`) con la misma
  condición `@ConditionalOnProperty(jwt.filter.enabled)`.
- Cada microservicio solo declara la dependencia a `common-security` y conserva
  su propia configuración (`jwt.secret`, `jwt.filter.public-paths`,
  `jwt.filter.public-get-prefixes`), que es donde vive la variabilidad por
  servicio (p. ej. los `GET` públicos de catálogo).
- Las dependencias JJWT viven únicamente en `common-security`; los servicios
  dejaron de declararlas en su `pom.xml`.

## Consecuencias

### Positivas

- **Defensa en profundidad**: aunque un servicio interno quede expuesto, ya no
  basta con inventar headers; se exige un JWT válido y vigente.
- **Llamadas internas protegidas**: `pedidos → catalogo` y `pedidos → auth`
  viajan autenticadas, cerrando el hueco de las rutas de stock y de validación
  de usuario.
- **Controllers sin cambios**: al reconstruir la identidad desde los *claims*, el
  helper `GatewayAuth` y los endpoints existentes funcionan igual.
- **Una sola clave**: se mantiene el modelo de un único `JWT_SECRET` compartido,
  sin infraestructura adicional.

### Negativas / Compromisos

- **Acoplamiento a `common-security`**: los servicios comparten un mismo filtro;
  un cambio en su firma o comportamiento afecta a todos a la vez. Es aceptado
  porque el objetivo de seguridad es idéntico en todos y es el mismo tipo de
  acoplamiento que ya introduce `common-events`.
- **Suplanta el ADR-0002 en una parte de su justificación**: el gateway deja de
  ser el único que toca JWT; cualquiera de las capas puede rechazar. Hay que
  mantener `JWT_SECRET` sincronizado en todos los despliegues.
- **Configuración por servicio**: cada microservicio declara la dependencia a
  `common-security` y configura `jwt.secret` y los prefijos públicos del filtro;
  la superficie de configuración aumenta (mitigada al centralizar el código en
  el módulo común).
- **Secreto compartido estático**: un `JWT_SECRET` comprometido sigue
  comprometiéndolo todo (riesgo ya documentado en ADR-0002); la rotación y los
  secretos por servicio quedan como trabajo futuro.