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

## Diagramas C4 (Modelo C4)

| Nivel | Diagrama |
|---|---|
| Nivel 1 — Contexto | [C4 Contexto](../diagrams/c4-contexto.md) |
| Nivel 2 — Contenedores | [C4 Contenedores](../diagrams/c4-contenedores.md) |
| Nivel 3 — Componentes | [C4 Componentes — flujo pedido/pago/notificación](../diagrams/c4-componentes-flujo-pedido.md) |
