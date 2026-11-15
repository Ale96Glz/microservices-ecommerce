# ADR-0020: El monto del pago debe coincidir con el total del pedido (422)

- **Estatus:** Aceptado
- **Fecha:** 2026-11

## Contexto y Problema

`POST /api/v1/pago` (gateway → `pagos-service`) recibe `pedidoId` y `monto`. Hasta
ahora validaba solo que `monto > 0` y que no superara `pagos.monto-maximo-aprobado`
(100.00 por defecto, ADR-0014). **Nunca comparaba `monto` con el total real del
pedido.**

Consecuencia (hallazgo H-PAGO-01, severidad alta — dinero):

- Pagar **de menos**: pedido de total `30.00`, `monto=0.01` → pago `PROCESADO` y
  pedido `PAGADO`.
- Pagar **de más**: pedido `30.00`, `monto=31.00` → pago `PROCESADO` y el asiento
  del pago descuadra contra el pedido.

Un cuerpo HTTP alterado (o un cliente que no lee el total) rompe el invariante de
negocio **monto = total del pedido** y deja como venta cerrada un cobro que no
corresponde.

Este caso es **distinto** del que resolvió el ADR-0014: allí "`monto` supera el
límite aprobado" es un **resultado de pago legítimo** que debe persistirse como
`RECHAZADO` y disparar la saga de compensación (ADR-0013). Aquí, en cambio, el
`monto` no coincide con el total: es una **petición inválida**, no una decisión de
pago. El pago no debe existir, el pedido no debe moverse y no debe publicarse
ningún evento.

## Opciones Evaluadas

1. **`422 Unprocessable Content` sin persistir (elegida)**: la petición está bien
   formada (JSON válido, `monto > 0`) pero es semánticamente inaplicable contra el
   total del pedido (RFC 9110). El pago no se crea, el pedido sigue `CREADO` y no
   hay evento.
   - Pros: distingue con precisión tres clases de error ya presentes en el
     servicio — `400` (forma/Bean Validation), `409` (conflicto de estado: "pedido
     ya pagado" / "no está CREADO") y `422` (regla de negocio del contenido).
     Mantiene el invariante sin ensuciar la auditoría de pagos con intentos
     imposibles ni disparar compensaciones por un request malformado.
   - Contras: introduce un código que el servicio no usaba; los clientes deben
     tratar una rama más.
2. **`409 Conflict`**: conflicto con el estado del pedido.
   - Pros: ya se usa para "pedido ya pagado"; cambio mínimo.
   - Contras: el `409` del RFC es un conflicto con el estado **del recurso
     destino** (el pago, que aún no existe), no con el de **otro** recurso (el
     pedido). Mezcla estado con validación de contenido.
3. **`400 Bad Request`**: tratarlo como validación.
   - Pros: muy estándar y ya se devuelve para `monto` obligatorio/`> 0`.
   - Contras: hoy `400` significa "forma del payload"; reusarlo confunde la
     validación de forma con una regla de negocio que depende de otro servicio.
4. **`201` + pago `RECHAZADO` persistido** (simétrico al ADR-0014).
   - Pros: consistente con "todo intento de pago queda auditado".
   - Contras: un `monto` que no iguala al pedido no es un intento de pago real;
     persistirlo como `RECHAZADO` contaminaría el histórico, podría disparar la
     saga de compensación (CANCELADO + restock) por un request inválido y dejaría
     al cliente creer que hubo una decisión de pago cuando en realidad envió mal
     el dato.

## Decisión

1. Antes de persistir, `pagos-service` obtiene el pedido (`PedidoClient.validarPagable`,
   que ya valida estado `CREADO` y propiedad) y exige
   `request.monto.compareTo(pedido.total) == 0` (comparación sin escala: `30`,
   `30.0` y `30.00` son iguales).
2. Si no coincide (o el pedido no tiene total), lanza `MontoNoCoincideException`
   → **`422`**, con un mensaje que **nombra el total**:
   `"El monto 121 no coincide con el total del pedido 120.00"`.
3. El pago **no se persiste**, el pedido **no** pasa a `PAGADO` y **no** se publica
   `PaymentProcessedEvent`. El stock no queda como venta cerrada.
4. `409` se reserva para conflictos de estado (pedido ya pagado / no `CREADO`) y
   `400` para la forma del payload; `422` queda para esta regla de negocio.
5. La ruta asíncrona (`procesarDesdeEvento`, consumidor de `order-created`) no
   cambia: usa `event.total()`, que por construcción iguala el total del pedido.
6. El caso solo es alcanzable por el `POST` manual cuando el pedido está `CREADO`
   y sin pago `PROCESADO`; en Kubernetes con Kafka activo eso ocurre tras
   `reactivar` (ADR-0016). El smoke E2E lo comprueba ahí.

## Consecuencias

### Positivas

- **Invariante garantizado**: `monto == total` en todo pago creado; desaparece el
  descuadre por pagar de menos o de más.
- **Taxonomía de errores clara**: `400` forma, `409` estado, `422` regla de
  negocio; el mensaje nombra el total, así que el cliente sabe qué corregir.
- **Sin efectos colaterales**: no se ensucia la auditoría de pagos ni se dispara
  la saga por un request inválido.

### Negativas / Compromisos

- **Cambio de contrato**: la versión desplegada devolvía `409`; ahora es `422`.
  Hay que **re-etiquetar** (release de imágenes) y actualizar la suite SDET y
  `docs/escenarios-de-compra.md`, que fijaban `409`.
- **Dependencia del pedido**: la validación requiere una llamada HTTP a
  `pedidos-service`; ya existía (`validarPagable`), así que no añade latencia
  nueva, pero ata el alta de pago a la disponibilidad de pedidos.
