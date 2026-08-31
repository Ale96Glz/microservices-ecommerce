# C4 — Nivel 2: Diagrama de Contenedores

```mermaid
graph LR
    Cliente["Usuario / Admin<br/>Browser / App"]

    subgraph k8s["Kubernetes — namespace ecommerce"]
        Ingress["Ingress<br/>ingress-nginx"]
        GW["api-gateway<br/>Spring Cloud Gateway<br/>:8080"]
        AUTH["auth-service<br/>Spring MVC<br/>:8081"]
        CAT["catalogo-service<br/>Spring MVC<br/>:8082"]
        PED["pedidos-service<br/>Spring MVC<br/>:8083"]
        PAG["pagos-service<br/>Spring MVC<br/>:8084"]
        NOT["notificaciones-service<br/>Spring MVC<br/>:8085"]
        KAFKA["Kafka broker<br/>bitnami/kafka:3.7"]
        KUI["Kafka UI<br/>:8089"]

        subgraph DBs["Bases PostgreSQL (una por servicio)"]
            DB_AUTH[("auth_db")]
            DB_CAT[("catalogo_db")]
            DB_PED[("pedidos_db")]
            DB_PAG[("pagos_db")]
            DB_NOT[("notificaciones_db")]
        end
    end

    Cliente -->|"HTTPS / REST"| Ingress
    Ingress -->|"HTTP :8080"| GW

    GW -->|"HTTPS / REST<br/>/api/v1/auth/**"| AUTH
    GW -->|"HTTPS / REST<br/>/api/v1/producto/**"| CAT
    GW -->|"HTTPS / REST<br/>/api/v1/pedido/**"| PED
    GW -->|"HTTPS / REST<br/>/api/v1/pago/**"| PAG
    GW -->|"HTTPS / REST<br/>/api/v1/notificacion/**"| NOT

    AUTH -->|"TCP / JDBC"| DB_AUTH
    CAT -->|"TCP / JDBC"| DB_CAT
    PED -->|"TCP / JDBC"| DB_PED
    PAG -->|"TCP / JDBC"| DB_PAG
    NOT -->|"TCP / JDBC"| DB_NOT

    PED -->|"REST síncrono<br/>GET producto/{id}"| CAT

    PED -->|"AMQP / Kafka<br/>topic: order-created"| KAFKA
    PAG -->|"AMQP / Kafka<br/>topic: order-created"| KAFKA
    PAG -->|"AMQP / Kafka<br/>topic: payment-processed"| KAFKA
    NOT -->|"AMQP / Kafka<br/>topics: order-created, payment-processed"| KAFKA

    KAFKA -->|"UI / HTTP"| KUI
```

**Notas de estilo C4:**

| Elemento | Convención C4 | En el diagrama |
|---|---|---|
| Actor externo | persona | usuario/adminsitrador |
| Contenedor (proceso) | Microservicio | gateway + 5 servicios + broker |
| Base de datos | contenedor de datos | `DB_*` |
| Comunicación | protocolo sobre la flecha | HTTPS/REST, JDBC, Kafka |

**Observaciones de arquitectura:**
- Único punto de entrada externo: `Ingress → api-gateway` (ADR-0002, ADR-0005).
- `pedidos → catálogo` es la única llamada REST síncrona; el resto de la cadena
  aguas abajo es asíncrona por Kafka (ADR-0003).
- Persistencia aislada por dominio (ADR-0004).
