# Decisiones de arquitectura (ADR)

Registro de decisiones de arquitectura del proyecto. Cada ADR sigue el formato
Michael Nygard (Contexto → Decisión → Consecuencias).

| ID | Título | Estatus |
|---|---|---|
| [ADR-0001](./adr/ADR-0001-microservicios-spring-boot.md) | Arquitectura de microservicios con Spring Boot | Aceptado |
| [ADR-0002](./adr/ADR-0002-gateway-jwt-centralizado.md) | Spring Cloud Gateway con validación JWT centralizada (trusted-headers) | Aceptado |
| [ADR-0003](./adr/ADR-0003-kafka-asiincrono.md) | Comunicación asíncrona entre servicios mediante Kafka | Aceptado |
| [ADR-0004](./adr/ADR-0004-persistencia-por-servicio.md) | Persistencia por servicio (H2 local, PostgreSQL Docker) | Aceptado |
| [ADR-0005](./adr/ADR-0005-ingress-api-gateway.md) | Exposición del api-gateway mediante Ingress en Kubernetes | Aceptado |
| [ADR-0006](./adr/ADR-0006-jwt-por-microservicio.md) | Validación JWT dentro de cada microservicio (defensa en profundidad) | Aceptado |
| [ADR-0007](./adr/ADR-0007-exposicion-puertos-internos.md) | Exposición de puertos internos y red por defecto | Aceptado |
| [ADR-0008](./adr/ADR-0008-https-ingress.md) | HTTPS con TLS por defecto mediante Ingress | Aceptado |
| [ADR-0009](./adr/ADR-0009-secretos-sealed.md) | Gestión segura de secretos con Kubernetes Sealed Secrets | Aceptado |
| [ADR-0010](./adr/ADR-0010-rate-limiting-redis.md) | Rate limiting distribuido con Redis en el API Gateway | Aceptado |
| [ADR-0011](./adr/ADR-0011-observabilidad.md) | Observabilidad: logs estructurados, métricas y trazabilidad distribuida | Aceptado |
| [ADR-0012](./adr/ADR-0012-ci-y-smoke-test.md) | Pipeline CI y smoke tests E2E | Aceptado |
| [ADR-0013](./adr/ADR-0013-compensacion-stock-saga-outbox.md) | Compensación de stock ante rechazo de pago (saga con outbox) | Aceptado |
| [ADR-0014](./adr/ADR-0014-contrato-http-pago-201-motivechazo.md) | Contrato HTTP de creación de pago (201 como recurso creado, aprobación en el cuerpo) | Aceptado |
| [ADR-0015](./adr/ADR-0015-arranque-ordenado-gateway-auth.md) | Arranque ordenado del API Gateway (gateway espera a auth y redis sanos) | Aceptado |
| [ADR-0016](./adr/ADR-0016-reintento-pago-rechazado.md) | Reintento de pago tras rechazo (intentos por pedido + reactivación explícita) | Aceptado |

## Guías operativas

| Guía | Contenido |
|---|---|
| [Escenarios de compra](../escenarios-de-compra.md) | Flujos E2E alineados con el smoke |
| [Camino a producción](../produccion.md) | Veredicto de madurez y plan de endurecimiento |

## Diagramas C4 (Modelo C4)

| Nivel | Diagrama |
|---|---|
| Nivel 1 — Contexto | [C4 Contexto](../diagrams/c4-contexto.md) |
| Nivel 2 — Contenedores | [C4 Contenedores](../diagrams/c4-contenedores.md) |
| Nivel 3 — Componentes | [C4 Componentes — flujo pedido/pago/notificación](../diagrams/c4-componentes-flujo-pedido.md) |
