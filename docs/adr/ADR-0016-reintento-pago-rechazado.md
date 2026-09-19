# ADR-0016: Reintento de pago tras rechazo (intentos por pedido + reactivación explícita)

- **Estatus:** Aceptado
- **Fecha:** 2026-09

## Contexto y Problema

Cuando un pago se rechaza (`Pago` con `estado=RECHAZADO` porque el monto supera
`pagos.monto-maximo-aprobado`), el flujo actual deja al cliente **sin opciones**:

1. `pagos-service` impone **un único pago por pedido**: `existsByPedidoId` →
   `409` si se intenta pagar de nuevo (`PagoServiceImpl.guardarPago`), con
   unicidad de BD (`unique` sobre `pedido_id`).
2. `pedidos-service` pasa el pedido a `CANCELADO` y encola la compensación de
   stock (ADR-0013); `procesarResultadoPago` solo actúa sobre pedidos en
   `CREADO`, así que el resultado de un pago posterior se **ignora**.

Resultado: un comprador cuyo pago se rechazó debe **crear un pedido nuevo**
desde cero (re-seleccionar productos, re-validar stock, perder sus intentos).
El ADR-0014 dejó este hueco registrado como follow-up en la hoja de ruta.

Además, `POST /api/v1/pago` **no valida** que el `pedidoId` pertenezca al
usuario ni que el pedido exista/sea pagable: cualquier autenticado puede
"pagar" un `pedidoId` arbitrario con un monto arbitrario; el consumidor de
pedidos ignora el evento si el pedido no está en `CREADO`, dejando intentos de
pago huérfanos. La reactivación del flujo de reintento obliga a cerrar ese hueco.

## Opciones Evaluadas

1. **Reintento con intentos + reactivación explícita del pedido (elegida)**:
   - `Pago` gana `intento` (1..n); la unicidad pasa de `pedido_id` a
     `(pedido_id, intento)`. Un nuevo intento solo se crea cuando el último
     pago del pedido es `RECHAZADO`; **a lo sumo un `PROCESADO` por pedido**
     (protege contra doble cobro). Cada intento se conserva (auditoría).
   - El pedido gana `motivoCancelacion` interno (`PAGO_RECHAZADO` | `USUARIO`)
     y un endpoint `PUT /api/v1/pedido/{id}/reactivar` que solo funciona si
     `CANCELADO` con `motivoCancelacion=PAGO_RECHAZADO`: **re-valida y
     re-reserva stock** (misma lógica que `crear`) y devuelve el pedido a
     `CREADO`, **sin re-publicar `order-created`** (evita re-notificar y
     re-trigger del pago automático por evento). No existe la transición
     `CANCELADO → PAGADO`: el pago posterior sigue consumiéndose sobre
     `CREADO` y avanza a `PAGADO` por el camino normal.
   - `POST /pago` **valida** (vía pedidos) que el pedido exista, sea del
     usuario y esté `CREADO` antes de crear el intento; sin esa validación, el
     reintento sería una vía de cobro sin pedido pagable.
   - Pros: inventario **siempre consistente** (la re-reserva es explícita),
     máquina de estados de pedido intacta (`CREADO → PAGADO/CANCELADO`), la
     saga y el dedup por `eventId` (ADR-0013) no cambian, auditoría completa de
     intentos y se cierra el hueco de integridad de validación.
   - Contras: flujo de dos pasos para el cliente (`reactivar` y luego
     `POST /pago`), nueva dependencia HTTP `pagos → pedidos`, migración de la
     unicidad en BD.
2. **Reintento "de un clic" (`CANCELADO → PAGADO` ante un `PROCESADO`
   posterior)**: no requiere reactivar.
   - Pros: experencia de un paso.
   - Contras: el stock ya se liberó al cancelar → el pedido quedaría `PAGADO`
     **sin reserva de inventario**; re-reservar "en el pago" obligaría a una
     saga de reversa si no hay stock (cobrado sin stock) o a validación
     síncrona dentro del consumidor — más acoplamiento y casos de fallo.
3. **Idempotency-Key HTTP genérica**: el cliente manda `Idempotency-Key`;
   misma clave = misma respuesta, clave nueva tras rechazo = intento nuevo.
   - Pros: estándar y reusable para cualquier `POST`.
   - Contras: tabla/TTL/almacenamiento de claves y mayor superficie; para este
     dominio (rechazo simulado por umbral) es infraestructura sobrada.
4. **Sin código: reintento = nuevo pedido** (workaround actual).
   - Pros: cero cambios.
   - Contras: no resuelve el caso; el hueco del ADR-0014 permanece.

## Decisión

1. **Modelo de intentos en `pago`**: columna `intento` (`int`, 1..n) y
   unicidad `(pedido_id, intento)`; se elimina `unique(pedido_id)`. Reglas:
   - `guardarPago`: si existe un pago `PROCESADO` para el pedido → `409`
     ("el pedido ya está pagado"); si el último es `RECHAZADO` → se crea un
     intento nuevo con `intento = último + 1`; si no existe pago → `intento=1`.
   - `procesarDesdeEvento` (pago automático por `order-created`) y
     `obtenerPorPedido`/`GET /pago/pedido/{id}` pasan a devolver el **último**
     intento (`findFirstByPedidoIdOrderByIdDesc`), evitando
     `NonUniqueResultException` con múltiples intentos.
2. **Pedido re-procesable por rechazo**: columna interna `motivoCancelacion`
   (`PAGO_RECHAZADO` al cancelarse por el evento de pago; `USUARIO` en
   `cancelar`). Nuevo `PUT /api/v1/pedido/{id}/reactivar`:
   - válido solo si `estado=CANCELADO` y `motivoCancelacion=PAGO_RECHAZADO`;
     en caso contrario `409`.
   - re-valida y **re-descuenta stock** como en `crear` (reusa validación de
     disponibilidad); si hay stock insuficiente → `409` y el pedido sigue
     `CANCELADO`.
   - pone el pedido en `CREADO` y **no** publica `order-created`.
   - No se admite `CANCELADO → PAGADO`; `procesarResultadoPago` sigue actuando
     solo sobre `CREADO` (sin cambios de regla en la saga).
3. **Validación de pagabilidad en `POST /api/v1/pago`** (pagos → pedidos):
   antes de crear el intento se consulta al `pedidos-service` y se verifica que
   el pedido exista, pertenezca al usuario autenticado y esté `CREADO`; de lo
   contrario `404`/`403`/`409` según el caso. El pago por evento
   (`procesarDesdeEvento`) no requiere esta validación (el evento ya es
   interno y fiable).
4. **Contrato de respuesta**: `POST /pago` sigue devolviendo `201` con el
   intento creado (recurso creado, ADR-0014) e incluye `intento` en
   `PagoResponseDTO`; `409` ahora significa "pedido ya pagado" o "pedido no
   pagable", no "ya existe un pago".
5. **Persistencia y migración**: esquema H2/PostgreSQL — la unicidad pasa a
   `(pedido_id, intento)` (`uk_pago_pedido_intento`) y se elimina la antigua
   `unique(pedido_id)`. Como `ddl-auto=update` no elimina la restricción vieja,
   `pagos-service` ejecuta al arranque (solo PostgreSQL) una migración que
   detecta y elimina cualquier `unique` sobre `pedido_id` (idempotente).
   `pedido` gana la columna `motivo_cancelacion`.
6. **Umbral de aprobación ajustable en runtime**: el máximo aprobado se lee de
   la tabla `configuracion` (clave `monto_maximo_aprobado`, sembrada al arranque
   con `pagos.monto-maximo-aprobado`) en **cada** pago, permitiendo cambiar el
   umbral sin reiniciar el servicio (así el reintento puede aprobar en el smoke).
7. **Compensación por ciclo**: el `eventId` del `RESTOCK_REQUIRED` incorpora
   origen/intento (`pedidoId-productoId-intentoN` para rechazo de pago,
   `pedidoId-productoId-usuario` para cancelación manual); el dedup de catálogo
   (ADR-0013) aplica una sola vez por **ciclo**, evitando perder stock en ciclos
   repetidos rechazo → reactivar → rechazo.
8. **Smoke E2E**: tras el rechazo se hace `reactivar` → re-reserva stock →
   `POST /pago` (con el umbral ya subido en runtime) → pedido `PAGADO`,
   verificando `intento=2`, stock re-reservado y sin duplicados de notificación
   de pedido.

## Consecuencias

### Positivas

- **Flujo completo de reintento**: el cliente puede corregir y pagar sin
  recrear el pedido; todos los intentos quedan auditados.
- **Inventario consistente**: la re-reserva al reactivar evita `PAGADO` sin
  stock; la compensación idempotente (ADR-0013) no se altera.
- **Cierre de hueco de integridad**: `POST /pago` valida propiedad y estado del
  pedido; adiós a pagos huérfanos sobre pedidos inexistentes/ajenos.
- **Máquina de estados de pedido intacta**: se evita la transición
  `CANCELADO → PAGADO`.

### Negativas / Compromisos

- **Flujo de dos pasos** para el cliente (`reactivar` + `POST /pago`); un
  cliente que pague sin reactivar recibe `409` (pedido no pagable).
- **Nueva dependencia síncrona `pagos → pedidos`** para la validación de
  pagabilidad (misma naturaleza que `pedidos → catálogo/auth` existentes).
- **Migración de unicidad** en `pago`: requiere eliminar el `unique` antiguo de
  forma controlada en los tres entornos (H2 local, Postgres Docker, k8s).
- **Reactivación sin re-publicación de `order-created`** exige disciplina para
  no re-notificar ni re-disparar el pago automático.
- **El rechazo sigue siendo determinista** (monto > umbral): reintentar sin
  cambio solo aprobará si el umbral (DATO en runtime/config) cambió; se
  documenta que en la simulación el valor útil del reintento es con umbral
  ajustable.