# ADR-0014: Contrato HTTP de creación de pago (201 como recurso creado, aprobación en el cuerpo)

- **Estatus:** Aceptado
- **Fecha:** 2026-09

## Contexto y Problema

`POST /api/v1/pago` (gateway → `pagos-service`) recibe el pedido y su monto y
decide el resultado **de forma síncrona**: si `monto > pagos.monto-maximo-aprobado`
(el límite aprobado del usuario, 100.00 por defecto) el pago se crea con
`estado=RECHAZADO`; si no, con `estado=PROCESADO`. El `PagoController` devuelve
**siempre `201 CREATED`**, incluso cuando el "procesamiento" fue realmente un
rechazo.

Ese contrato es confuso para los clientes: un `201` sugiere un pago aceptado, y
el cliente solo descubre que fue rechazado al inspeccionar el cuerpo, que además
**no explica el motivo** del rechazo (`PagoResponseDTO` carecía de cualquier
campo descriptivo).

Restricciones del dominio que condicionan la solución:

1. El pago **siempre se persiste** (incluso rechazado) por auditoría: una
   transacción financiera intentada debe quedar registrada con su resultado.
2. La cadena Kafka depende de este contrato: el `PaymentProcessedEvent` con
   `estado=RECHAZADO` alimenta la saga de compensación (ADR-0013) y las
   notificaciones. Si el servicio no devolviera el pago y no publicara el evento,
   la saga nunca tendría lugar.
3. `existsByPedidoId` (único por pedido) hace que **reintentar el pago de un
   pedido rechazado devuelva `409 Conflict`**: no hay flujo de reintento.

## Opciones Evaluadas

1. **`201` + resultado en el cuerpo + `motivoRechazo` (elegida)**: `201 CREATED`
   se interpreta como "recurso pago creado" (el intento se registró y su
   resultado es `estado` + `motivoRechazo` en el cuerpo). El evento
   `PaymentProcessedEvent` y la notificación propagan el motivo.
   - Pros: mantiene el invariante de auditoría (todo intento queda persistido),
     no rompe la saga/notificaciones, devuelve al cliente el motivo legible y es
     coherente con el patrón REST de "crear un recurso cuyo procesamiento ya se
     resolvió" (como un ticket de importe con resolución en frío).
   - Contras: el `201` con `estado=RECHAZADO` exige documentar bien el
     semántico en el ADR/README para no confundir clientes.
2. **`422 Unprocessable Entity` + pago no persistido**: el servidor rechaza la
   petición sin crear nada.
   - Pros: semántica HTTP "el servidor entiende pero no procesa" más literal.
   - Contras: **rompe la auditoría** (no queda registro del intento), **rompe la
     saga de compensación y las notificaciones** (no hay evento RECHAZADO que
     disparar — salvo que se publicara un evento sin entidad, duplicando el
     outbox) y complica al cliente (otra rama de error más para un flujo que ya
     es normal: una compra que supera el límite).
3. **`202 Accepted` + resultado asíncrono**: aceptar y procesar después.
   - Pros: modela un procesamiento lento.
   - Contras: el resultado ya se resuelve **síncronamente** en el mismo request
     (el servicio decide al instante); añade polling/webhook sin necesidad y
     oculta el rechazo.

## Decisión

1. **`POST /api/v1/pago` responde `201 CREATED` en todos los casos procesables**
   (sin `409`/`404` previos): el `201` significa "intento de pago registrado", y
   el resultado de la aprobación se comunica en el cuerpo con:
   - `estado`: `PROCESADO` o `RECHAZADO`.
   - `motivoRechazo`: presente y legible solo cuando `estado=RECHAZADO` (p. ej.
     `"Monto excede el máximo aprobado (100.00)"`), `null` en caso contrario.
2. **`PagoResponseDTO` gana `motivoRechazo`** y `pago.motivo_rechazo` se persiste
   en la entidad (auditoría del motivo).
3. **El evento de contrato `PaymentProcessedEvent` gana el campo opcional
   `motivoRechazo`** (adición compatible); el fixture del contrato y los tests de
   productor/consumidor se actualizan.
4. **`notificaciones-service` incluye el motivo** en el mensaje de la notificación
   cuando el pago es rechazado ("Motivo: Monto excede…").
5. **Gap de reintento documentado, no resuelto en este ADR**: la unicidad
   `existsByPedidoId` hace que reintentar el pago de un pedido rechazado dé
   `409 Conflict`. El flujo de reintento (intentos por pedido + reactivación
   explícita) se define y decide en el **ADR-0016**; este ADR solo lo registra
   como dependencia.
6. **Smoke E2E**: la parte 7 ya cubre el caso rechazado; se verifica además que la
   notificación incluya el motivo. No cambia el `201` esperado por el contrato.

## Consecuencias

### Positivas

- **Auditoría completa**: todo intento de pago (aceptado o rechazado) queda
  persistido con su motivo; el `201` es consistente con ese invariante.
- **Cliente informado**: el cuerpo indica el resultado y el motivo legible; la
  notificación al usuario refleja el mismo motivo.
- **Sin ruptura de la saga**: el `PaymentProcessedEvent` con `RECHAZADO` sigue
  disparando la compensación (ADR-0013) tal cual estaba.
- **Cambio compatible de contrato**: añadir un campo opcional al evento respeta a
  los consumidores existentes (los deserializadores desconocen los keys nuevos).

### Negativas / Compromisos

- **Semántica que requiere documentación**: el `201` con `estado=RECHAZADO`
  puede confundir a clientes que asumen "201 = aprobado"; se mitiga
  documentándolo aquí y en el contrato del evento.
- **Sin reintento de pago rechazado**: el `409` por unicidad sigue ahí; un
  comprador cuyo pago excede el límite no puede corregir y reintentar sin
  crear un pedido nuevo (follow-up).
- **Campo nulo en la respuesta feliz**: `motivoRechazo: null` en el JSON del pago
  aprobado es ruido menor para clientes que prefieren campos ausentes.