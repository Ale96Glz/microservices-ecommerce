# C4 — Nivel 3: Diagrama de Componentes (flujo de pedido → pago → notificación)

Este diagrama detalla los componentes internos de los tres servicios implicados
en la cadena principal de negocio.

```mermaid
graph LR
    subgraph pedidos["pedidos-service"]
        PedCtrl["PedidoController<br/>/api/v1/pedido"]
        PedSvc["PedidoServiceImpl"]
        CatalogoClient["CatalogoClient<br/>RestClient"]
        AuthClient["AuthClient<br/>RestClient"]
        OrderPub["OrderEventPublisher"]
        PedRepo["PedidoRepository"]
        PedDB[("pedidos_db")]
    end

    subgraph auth["auth-service"]
        UsuCtrl["UsuarioController<br/>GET /usuario/existe/{id}"]
        UsuSvc["UsuarioServiceImpl"]
        UsuRepo["UsuarioRepository"]
        UsuDB[("auth_db")]
    end

    subgraph catalogo["catalogo-service"]
        ProdCtrl["ProductoController<br/>GET /producto/{id}, PUT /producto/{id}/stock"]
        ProdSvc["ProductoServiceImpl"]
        ProdRepo["ProductoRepository"]
        CatDB[("catalogo_db")]
    end

    subgraph pagos["pagos-service"]
        PagCtrl["PagoController<br/>/api/v1/pago"]
        OrderListener["OrderCreatedListener"]
        PagSvc["PagoServiceImpl"]
        PaymentPub["PaymentEventPublisher"]
        PagRepo["PagoRepository"]
        PagDB[("pagos_db")]
    end

    subgraph notif["notificaciones-service"]
        OrderNotif["OrderCreatedListener"]
        PaymentNotif["PaymentProcessedListener"]
        NotifSvc["NotificacionServiceImpl"]
        NotifRepo["NotificacionRepository"]
        NotifDB[("notificaciones_db")]
    end

    KAFKA["Kafka broker"]

    PedCtrl -->|"GatewayAuth.requireUser"| PedSvc
    PedSvc -->|"validarUsuario(id)<br/>REST / GET"| AuthClient
    AuthClient -->|"GET /usuario/existe/{id}"| UsuCtrl
    UsuCtrl --> UsuSvc
    UsuSvc -->|"consulta"| UsuRepo
    UsuRepo -->|"JDBC"| UsuDB
    UsuSvc -->|"UsuarioValidacionDTO"| AuthClient
    PedSvc -->|"obtenerProducto(id)<br/>REST / GET"| ProdCtrl
    PedSvc -->|"descontarStock(id, cant)<br/>REST / PUT"| CatalogoClient
    ProdCtrl --> ProdSvc
    ProdSvc -->|"consulta / descuenta<br/>stock atómico"| ProdRepo
    ProdRepo -->|"JDBC"| CatDB
    ProdSvc -->|"ProductoResponseDTO"| CatalogoClient
    PedSvc -->|"persiste Pedido (CREADO)"| PedRepo
    PedRepo -->|"JDBC"| PedDB
    PedSvc -->|"OrderCreatedEvent"| OrderPub
    OrderPub -->|"topic: order-created<br/>AMQP/Kafka"| KAFKA

    KAFKA -->|"consumer group: pagos-service"| OrderListener
    OrderListener --> PagSvc
    PagCtrl -->|"procesar (manual)<br/>GatewayAuth.requireUser"| PagSvc
    PagSvc -->|"valida existsByPedidoId<br/>+ persiste Pago (PROCESADO)"| PagRepo
    PagRepo -->|"JDBC"| PagDB
    PagSvc -->|"PaymentProcessedEvent"| PaymentPub
    PaymentPub -->|"topic: payment-processed"| KAFKA

    KAFKA -->|"consumer group: notificaciones-service"| OrderNotif
    OrderNotif -->|"registra PEDIDO_CREADO"| NotifSvc
    KAFKA -->|"consumer group: notificaciones-service"| PaymentNotif
    PaymentNotif -->|"registra PAGO_PROCESADO"| NotifSvc
    NotifSvc -->|"persiste Notificacion"| NotifRepo
    NotifRepo -->|"JDBC"| NotifDB
```

**Lectura del flujo:**
1. El cliente crea un pedido en `pedidos-service` (requiere header `X-User-Id`).
2. `pedidos-service` valida la existencia del usuario contra `auth-service`
   (`GET /usuario/existe/{id}`).
3. Por cada producto, consulta precio/stock a `catalogo-service` y **descunta el
   stock** (`PUT /producto/{id}/stock`) de forma atómica.
4. Con los productos validados, persiste el `Pedido` (estado `CREADO`) y publica
   `OrderCreatedEvent` en Kafka.
5. `pagos-service` consume `order-created` (o recibe pago manual por API), persiste
   el `Pago` (estado `PROCESADO`, idempotente por `UNIQUE pedido_id`) y publica
   `PaymentProcessedEvent`.
6. `notificaciones-service` consume ambos tópicos y registra las notificaciones
   `PEDIDO_CREADO` y `PAGO_PROCESADO`.

**Nota sobre estados:** el `Pedido` aún no se actualiza a `PAGADO` (ningún
consumidor de `payment-processed` existe en `pedidos`); ese ciclo de estados queda
pendiente de la `Fase 2`.
