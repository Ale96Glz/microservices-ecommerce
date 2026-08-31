# ADR-0003: Comunicación asíncrona entre servicios mediante Kafka

- **Estatus:** Aceptado
- **Fecha:** 2023-10 (iniciativa del proyecto)

## Contexto y Problema

El flujo principal del negocio genera una cadena de trabajo entre dominios:

```
Pedido creado → pagos procesa → notificaciones registra el aviso
```

La pregunta es cómo propagar el estado a lo largo de esa cadena. Se evaluaron:

1. **Llamadas REST síncronas encadenadas** (pedidos → pagos → notificaciones):
   acopla temporalmente a los tres servicios; un fallo en notificaciones
   bloquearía (o exigiría reintentos) en la creación de pedidos.
2. **Eventos asíncronos con un broker** (escenarios: RabbitMQ/AMQP, Kafka):
   desacopla productores y consumidores, permite que cada servicio consuma a su
   ritmo y tolera caídas parciales.
3. **Polling de base de datos** (outbox/saga manual): más simple de operar pero
   menos idiomático y con mayor latencia.

Restricciones: el proyecto ya usa Docker para infraestructura y desea un broker
autogestionable de código abierto.

## Decisión

Se adopta **Apache Kafka** (bitnami/kafka 3.7, modo KRaft) como broker de eventos
en **docker-compose**, con **dos tópicos** y eventos compartidos en el módulo
`common-events`:

| Tópico | Productor | Consumidores |
|---|---|---|
| `order-created` | `pedidos-service` | `pagos-service`, `notificaciones-service` |
| `payment-processed` | `pagos-service` | `notificaciones-service` |

Eventos definidos como records Java en `common-events`:
`OrderCreatedEvent(pedidoId, usuarioId, total, creadoEn)` y
`PaymentProcessedEvent(pagoId, pedidoId, usuarioId, monto, estado, procesadoEn)`.
La serialización usa `JsonSerializer`/`JsonDeserializer` de spring-kafka con
`USE_TYPE_INFO_HEADERS=false` y `VALUE_DEFAULT_TYPE`/`TRUSTED_PACKAGES` explícitos.

La comunicación **descendente** es íntegramente por eventos. La consulta de
productos (`pedidos → catálogo`) se mantiene como llamada REST síncrona
(`RestClient`) porque necesita el precio/stock actual en el instante de crear el
pedido (ver `ADR-0001` y `Fase 2`).

**Modo sin broker:** Kafka está **deshabilitado por defecto en local**
(`*.kafka.enabled=false`). Los publicadores usan `ObjectProvider<KafkaTemplate>`
con `getIfAvailable()`, de modo que si no hay broker, el flujo continúa
funcional cuando se dispara manualmente por API (pago manual), y solo la
propagación asíncrona queda inactiva.

## Consecuencias

### Positivas

- **Desacoplamiento temporal**: crear un pedido no depende de que pagos o
  notificaciones estén operativos en ese instante.
- **Escalado de consumidores**: lecturas de notificaciones pueden escalar
  agregando consumidores en un consumer group.
- **Autocuración parcial**: si un consumidor cae, los eventos quedan pendientes
  en el tópico (retención de Kafka) y pueden reprocesarse.
- **Contratos compartidos**: los eventos viven en `common-events`, lo que
  versiona el contrato de integración.

### Negativas / Compromisos

- **Consistencia eventual**: el pedido puede quedar `CREADO` mientras pagos aún no
  lo procesa; no hay transacción distribuida.
- **Estados sin transición automática**: hoy `Pedido.PAGADO` no se activa porque
  `pedidos-service` no escucha `payment-processed`; `Pago.RECHAZADO` nunca se
  persiste. El ciclo de estados está pendiente (`Fase 2` de la hoja de ruta).
- **Sin reintentos ni retry con backoff**: los listeners no implementan
  `DefaultErrorHandler` ni dead-letter topics (pendiente en `Fase 2`).
- **Sin Transactional Outbox**: el evento se publica después de persistir el
  pedido, lo que abre una ventana de inconsistencia si la publicación falla tras
  el commit (pendiente en `Fase 2`).
- **Kafka desactivado en local**: el desarrollador debe levantar docker-compose
  para ejercitar el flujo asíncrono completo.
- Mayor infraestructura de operación (broker + Kafka UI en `:8089`).
