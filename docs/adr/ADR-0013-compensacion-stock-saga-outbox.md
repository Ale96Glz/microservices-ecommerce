# ADR-0013: Compensación de stock ante rechazo de pago (saga con outbox)

- **Estatus:** Aceptado
- **Fecha:** 2026-09

## Contexto y Problema

`pedidos-service` descuenta stock de `catalogo-service` de forma síncrona al
crear el pedido (`PUT /api/v1/producto/{id}/stock`). Cuando el pago se rechaza
(`estado=RECHAZADO`) o el usuario cancela un pedido en estado `CREADO`, el stock
reservado debe devolverse al catálogo.

Hasta este ADR, la compensación se hacía **síncrona dentro de la transacción del
listener** (`PedidoServiceImpl.procesarResultadoPago` → rama `RECHAZADO` →
`catalogoClient.reponerStock` por cada ítem), con tres problemas de fallo:

1. **Límite de transacción distribuida**: el listener llamaba HTTP a catálogo
   **dentro** de su transacción JPA. Si el restock respondía 200 en catálogo
   (transacción de catálogo confirmada) pero el proceso moría antes del commit
   local, la transacción de pedidos se revertía (pedido sigue `CREADO`) y la
   re-entrega del evento volvería a reponer el stock → **doble restock (stock
   inflado)**. La guarda `estado != CREADO` no cubre esta ventana.
2. **Compensación no garantizada**: `reponerStock` no es idempotente (UPDATE
   aditivo `stock = stock + cantidad`) y, si catálogo estuviera caído los 3
   intentos, el evento iba al DLT sin reprocesamiento automático → pedido
   `CREADO` con stock reservado **para siempre**.
3. **No hay registro de la intención**: la intención de reponer no quedaba
   persistida en ningún sitio fuera del impacto efímero de una llamada HTTP.

## Opciones Evaluadas

1. **Saga de compensación con outbox + dedup en catálogo (elegida)**:
   - En la misma transacción que fija `CANCELADO` se escriben eventos
     `RestockRequestedEvent` (uno por ítem) en la tabla outbox (el mismo motor
     que ya publica `order-created`); el `OutboxPublisher` los publica a Kafka
     (`RESTOCK_REQUESTED`).
   - `catalogo-service` se convierte en **consumidor Kafka** del tópico y aplica
     la reposición **atómica con una tabla de idempotencia por `eventId`**:
     `restock_event(event_id PK, pedido_id, producto_id, cantidad, aplicado_en)`.
     Sin dedup, el reproceso (retry/DLT) volvería a sumar stock.
   - `eventId` es **determinista por ciclo de compensación**
     (`pedidoId-productoId-{intento|usuario}`, ver ADR-0016) para que reintentos
     de la misma intención se dedupliquen en catálogo, sin deduplicar ciclos
     distintos de un mismo pedido (rechazo → reactivar → nuevo rechazo).
   - Pros: compensación **garantizada** (el intento vive en el outbox hasta
     publicarse) e **idempotente** (una sola aplicación por intención), cierra la
     ventana del doble restock, no depende de la disponibilidad síncrona de
     catálogo y reutiliza el 80% del motor outbox existente.
   - Contras: catálogo deja de ser un servicio solo HTTP (nueva dependencia
     Kafka, consumidor, tópico, DLT, tabla de dedup) y la compensación pasa a ser
     **eventualmente consistente** (ventana de tiempo hasta que el outbox/el
     consumidor aplican la reposición).
2. **Flag `stock_restaurado` + barrido (`@Scheduled`)**:
   - Columna en `pedido_item` que registra la intención y un `@Scheduled` corrige
     los pendientes. Pros: sin tópico nuevo. Contras: no cierra la ventana del
     crash (si el proceso muere tras restock OK y antes de commit, el flag no
     queda grabado y se re-ejecuta), y sigue síncrono a merced de catálogo.
3. **Minimizar cambios**: añadir solo idempotencia por `eventId` en el endpoint
   `reponerStock` de catálogo manteniendo el paso síncrono. Cierra el doble
   restock pero **no** garantiza la compensación (catálogo caído = DLT y stock
   perdido).

## Decisión

1. **Compensación garantizada por saga**: en `pedidos-service`, `cancelar` y la
   rama `RECHAZADO` de `procesarResultadoPago` fijan `CANCELADO` y **encolan en
   el outbox** un `RESTOCK_REQUIRED` por ítem (misma transacción); se elimina la
   llamada síncrona `reponerStock` y el método huérfano del `CatalogoClient`.
2. **Outbox generalizado**: `OutboxPublisher` despacha por `tipoEvento`
   (`ORDER_CREATED` | `RESTOCK_REQUIRED`); constante
   `OutboxEvent.TIPO_RESTOCK_REQUIRED`.
3. **Nuevo evento de contrato**: `RestockRequestedEvent(eventId, pedidoId,
   productoId, cantidad, solicitadoEn)` en `common-events` + tópico
   `RESTOCK_REQUESTED` (`restock-requested`) + fixture de contrato.
4. **Consumidor en catálogo**: nuevo `KafkaConfig` (grupo `catalogo-service`,
   `auto.offset.reset=earliest`, retry 3 con backoff y DLT, igual que pagos/
   notificaciones), `RestockRequestedListener` → `RestockApplicationService` que
   aplica `reponerStock` y registra el `eventId` **en la misma transacción**
   (tabla `restock_event`). `catalogo.kafka.enabled` activa el consumidor
   (desactivado por defecto en local, habilitado en el compose).
5. **Contratos y tests**: contrato de productor (`RestockRequestedEventContractTest`
   en pedidos) y de consumidor (`RestockRequestedEventConsumerContractTest` en
   catálogo) sobre el fixture, más tests unitarios del dedup
   (`RestockApplicationServiceTest`) y del encolado (`PedidoServiceImplTest`).
6. **Smoke**: el escenario E2E valida la saga completa: pedido con total &gt;
   `100.00` → pago `RECHAZADO` → pedido `CANCELADO` → stock restaurado a su valor
   previo (parte 7 de `scripts/smoke-test.sh`).

## Consecuencias

### Positivas

- **Compensación garantizada**: la intención de restock es durable (outbox) y
  solo se marca `PUBLICADO` cuando Kafka confirma el produce (ADR-0019); no
  depende de catálogo en el momento del rechazo.
- **Idempotencia real**: la tabla `restock_event` (PK `eventId`) hace que reintentos,
  DLT o re-entrega apliquen la reposición **una sola vez** por intención y, desde el
  ADR-0016, una sola vez por **ciclo** de un pedido.
- **Autodocumentado y comprobable**: el contrato del evento, los tests y el paso 7
  del smoke convierten el flujo de compensación en un escenario ejecutable.
- **Coherencia de infraestructura**: catálogo sigue el mismo patrón de consumidor
  (retry + DLT + `earliest`) que pagos/notificaciones.

### Negativas / Compromisos

- **Catálogo gana Kafka**: nueva dependencia (`spring-kafka`, `common-events`),
  consumidor y tópico; una pieza más que operar (grupo, DLT, LAG).
- **Consistencia eventual**: entre el `RECHAZADO` y el restock efectivo hay una
  ventana (poll del outbox  5s + consumo Kafka); el smoke la asume con polling.
- **Rechazos no notificables por API**: el restock no se comunica al cliente;
  quien consulta el pedido ve `CANCELADO` y quien consulta el stock ve el valor
  restaurado cuando el consumidor lo aplica.
- **Reintentos manuales en DLT**: si el restock falla de forma permanente (p. ej.
  producto inexistente), queda en `restock-requested.DLT` y requiere reproceso
  manual, igual que el resto de eventos.
- **Tabla de dedup compartida por evento**: `restock_event` crece con cada
  compensación; volumen bajo, no requiere limpieza por ahora (se puede purgar por
  `aplicadoEn` en el futuro).