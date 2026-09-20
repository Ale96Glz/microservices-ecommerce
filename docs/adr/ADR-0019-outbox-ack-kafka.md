# ADR-0019: Outbox marca PUBLICADO solo con ack de Kafka

- **Estatus:** Aceptado
- **Fecha:** 2026-09

## Contexto y Problema

El patrón outbox (ADR-0013) deja el evento `PENDIENTE` en la misma transacción
del agregado y un `@Scheduled` lo publica a Kafka. Los publicadores usaban
`KafkaTemplate.send(...)` **sin esperar** el `CompletableFuture`. El hilo
marcaba `PUBLICADO` aunque el broker devolviera `UNKNOWN_TOPIC_OR_PARTITION`
o el envío no se completara. El siguiente ciclo ya no reintentaba; el restock
o el pago procesado no llegaban al consumidor.

El ADR-0018 declara los tópicos para evitar esa carrera. No cubre el fire-and-
forget del productor.

## Opciones Evaluadas

1. **Esperar el ack en el publicador (elegida):** `send(...).get(timeout)`.
   Si falla, el `OutboxPublisher` captura la excepción y deja `PENDIENTE`.
   Los consumidores siguen siendo idempotentes.
2. **Callback `whenComplete` asíncrono:** no bloquea el scheduler, pero
   complica el orden y el test; un fallo tardío puede solaparse con el
   siguiente poll.
3. **Replay automático de `PUBLICADO`:** rompe el significado del estado y
   duplica envíos sin necesidad si el produce es fiable.

## Decisión

1. `OrderEventPublisher`, `RestockEventPublisher` y `PaymentEventPublisher`
   esperan hasta 10s la confirmación del broker antes de registrar éxito.
2. Los `ProducerFactory` de dominio (y DLT) usan `acks=all`.
3. Un mensaje ya `PUBLICADO` que se pierde en Kafka (retención, borrado)
   **no** se reenvía solo: reset operativo a `PENDIENTE` o reproceso desde DLT.

## Consecuencias

### Positivas

- Un tópico ausente o un broker caído no “quema” la fila del outbox.
- Encaja con el Job de tópicos (ADR-0018) y con la idempotencia de catálogo.

### Negativas / Compromisos

- El hilo del `@Scheduled` se bloquea hasta el ack o el timeout (10s).
- `acks=all` con RF=1 en lab es el mismo nodo; el valor real aparece al subir
  réplicas.
