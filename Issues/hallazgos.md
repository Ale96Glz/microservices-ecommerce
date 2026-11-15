# Hallazgos de contrato

Defectos de negocio vistos desde el gateway. El caso en `casos.md` describe **lo que el API hace hoy**. Aquí va **lo que debería hacer** y cómo comprobarlo cuando se corrija el backend.

No seguir con más casos de pagos hasta cerrar este punto.

---

## H-PAGO-01 — El monto del pago no se valida contra el total del pedido

| Campo | Valor |
|---|---|
| Fecha | 10 de noviembre de 2026 |
| Severidad | Alta (dinero) |
| Servicio | Pagos, vía `POST /api/v1/pago` |
| Ambiente | Gateway `https://ecommerce.local`, API 1.2.2 |
| Estado | **Cerrado (backend).** `monto` ≠ total → `422` (ADR-0020; verificado en vivo). Pendiente en su repo: suite SDET y `casos.md` |

### Qué ocurre

`POST /api/v1/pago` trata `monto` como un número independiente. Si es mayor que 0 y no supera el tope de pasarela (100.00), el pago queda `PROCESADO` aunque no coincida con el total del pedido.

El tope de 100 sí se aplica: `monto` 1000 → 201 `RECHAZADO`, `Monto excede el máximo aprobado (100.00)`. Cero o ausente → 400. **Nada compara `monto` con el total del pedido.**

### Evidencia

Corrida de `PagosTest#unMontoDistintoAlDelPedidoQuedaProcesado` (10 nov 2026, 21:39 GMT).

1. `POST /api/v1/pedido` — pedido `282`, total **30**.
2. `POST /api/v1/pago` con `pedidoId` 282 y `monto` **31.0** (distinto al total, debajo de 100).
3. Respuesta **201**:

```json
{
  "id": 269,
  "pedidoId": 282,
  "usuarioId": 1770,
  "monto": 31.0,
  "estado": "PROCESADO",
  "motivoRechazo": null,
  "intento": 1
}
```

Los casos felices de la suite mandan siempre `"monto": 10.0` sin leer el total. Si el producto vale 10, el desajuste no se ve.

### Impacto

Un cliente (o un cuerpo HTTP alterado) puede:

- Pagar **de menos** (total 30, `monto` 0.01) y dejar el pedido pagado.
- Pagar **de más** (30 vs 31) y descuadrar el asiento (`monto` del pago ≠ total del pedido).

No es un 401 ni un JSON mal formado. Falta el invariante **monto = total del pedido**.

### Contrato actual (suite verde)

`docs/casos.md`: *Un monto distinto al del pedido queda procesado* → 201 `PROCESADO`.

Eso fija el comportamiento **hoy**. No es el comportamiento de negocio correcto.

### Contrato esperado tras la corrección

Antes de marcar `PROCESADO`, pagos debe leer el pedido y exigir que `monto` sea el total (misma escala decimal que el catálogo).

Si no coinciden, una de estas (elegir una y no mezclar):

| Opción | HTTP | Pedido | Pago |
|---|---|---|---|
| A (recomendada) | 409 | Sigue `CREADO` | No se persiste, o no queda `PROCESADO` |
| B | 201 | Sigue `CREADO` | `RECHAZADO` y `motivoRechazo` claro (p. ej. el monto no coincide con el total) |

> **Implementado:** opción A, pero con `422` en lugar de `409` para distinguirlo
> del `409` de estado ("pedido ya pagado"). Ver **ADR-0020**.

No usar 400 de validación Bean Validation salvo que el mensaje nombre el total. 400 hoy es solo “mayor a 0” / “obligatorio”.

El pedido **no** debe pasar a `PAGADO`. El stock no debe quedar como venta cerrada si el cobro no iguala el pedido.

### Cómo reproducir

```powershell
mvn -pl api-tests test "-Dtest=PagosTest#unMontoDistintoAlDelPedidoQuedaProcesado"
```

Hoy: verde (PROCESADO). Tras el arreglo: **rojo**, hasta actualizar el test y `casos.md`.

### Qué tocar en esta suite cuando el backend esté listo

1. `PagosTest#unMontoDistintoAlDelPedidoQuedaProcesado`: esperar 422 y
   `GET /api/v1/pedido/{id}` en `CREADO`.
2. Fila en `casos.md`: dejar de decir “queda procesado”.
3. Marcar este hallazgo como cerrado.

No borrar el caso: es el que demuestra el invariante.
