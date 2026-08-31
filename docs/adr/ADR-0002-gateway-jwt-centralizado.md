# ADR-0002: Spring Cloud Gateway con validación JWT centralizada (trusted-headers)

- **Estatus:** Aceptado
- **Fecha:** 2023-10 (iniciativa del proyecto)

## Contexto y Problema

La plataforma expone APIs REST bajo `/api/v1` para cinco dominios. Surgen dos
preguntas de seguridad centrales:

1. ¿Quién autentica al cliente? Cada servicio podría validar JWT por su cuenta,
   pero eso duplica la lógica de firma, el secreto compartido y el mantenimiento
   en cinco servicios.
2. ¿Cómo evitar que un cliente acceda directamente a un servicio interno saltándose
   el punto de entrada?

Restricción de equipo: desarrollo con Spring Boot sin experiencia previa en
patrones de identidad federada complejos (OAuth2/Keycloak). Se necesita una
solución pragmática de bajo acoplamiento.

Opciones evaluadas:

1. **Validar JWT en cada microservicio** (Spring Security + filtro por servicio):
   máxima defensa en profundidad, pero 5 configuraciones, 5 secretos compartidos
   y riesgo de divergencia.
2. **Gateway como único autenticador + propagación por headers** (patrón
   *trusted-headers*): una sola capa valida el token; el gateway inyecta la
   identidad y los servicios la consumen. Simple y consistente.
3. **OAuth2 / Keycloak / Identity Provider externo**: robusto y escalable, pero
   introduce infraestructura nueva y curva de aprendizaje alta para el alcance
   actual.

## Decisión

Se adopta **Spring Cloud Gateway** como único punto de entrada HTTP en el puerto
`8080`, que:

- Declara rutas por `Path` hacia cada servicio (`/api/v1/**`).
- Valida el **único** JWT en el `JwtAuthGlobalFilter` (GlobalFilter, order `-100`),
  con JJWT 0.12.6 y firma HMAC-SHA.
- Deja públicas solo rutas concretas: `/api/v1/auth/register`, `/api/v1/auth/login`,
  `/actuator/health*` y los **GET** de `/api/v1/producto/**` y `/api/v1/categoria/**`.
- Tras validar, **inyecta headers de identidad** hacia los servicios:
  `X-User-Id`, `X-User-Email`, `X-User-Rol`.
- **Elimina cualquier header `X-User-*` entrante** antes de reinyectarlo, para
  evitar suplantación de identidad.

Los servicios **no validan el JWT**; consumen los headers inyectados mediante el
helper `GatewayAuth` (`requireUser`, `requireAdmin`, `requireSelfOrAdmin`) y, a
nivel de Spring Security, deshabilitan sesiones (`STATELESS`) y permiten todo
(`anyRequest().permitAll()`), delegando la autenticación al gateway.

El filtro se habilita con `@ConditionalOnProperty(gateway.security.enabled)`, lo
que permite desactivar la seguridad localmente durante el desarrollo.

## Consecuencias

### Positivas

- **Una sola superficie de autenticación**: el token se valida una vez; los
  servicios no gestionan claves ni parser JWT.
- **Modelo de autorización uniforme**: los controllers usan un helper consistente
  (`GatewayAuth`) para "usuario autenticado", "admin" o "propio recurso".
- **Defensa contra acceso directo**: si un cliente golpea un puerto interno sin
  pasar por el gateway, los checks de `GatewayAuth` fallan por falta de headers
  (`403`).
- **Mantenimiento reducido**: un solo secreto `JWT_SECRET` compartido entre
  gateway y auth-service.
- **Seguridad desactivable** en local vía propiedad de configuración, agilizando
  el desarrollo.

### Negativas / Compromisos

- **Defensa en profundidad limitada**: si un servicio quedara expuesto
  directamente (p. ej. un misconfiguration de red), no valida el JWT por sí solo.
  Mitigación parcial: bloquear puertos internos (ver `Fase 3` de la hoja de ruta).
- **Confianza en headers inyectados**: cualquier servicio debe **confiar**
  ciegamente en `X-User-*`; el gateway es el único que debe permitirse inyectarlos.
- **Rutas públicas frágiles**: los GET del catálogo son públicos; si más adelante
  se requieren restricciones por contenido, habrá que endurecer el gateway.
- **Secreto compartido estático**: aun cuando se gestiona como secret de K8s, un
  solo `JWT_SECRET` comprometido invalida todo el ecosistema. La rotación de claves
  no está automatizada.
- La validación JWT es bloqueante dentro de un filtro reactivo; se ejecuta en
  `Schedulers.boundedElastic()` para evitar bloquear el event-loop de WebFlux,
  con la sobrecarga de cambio de scheduler asociada.
