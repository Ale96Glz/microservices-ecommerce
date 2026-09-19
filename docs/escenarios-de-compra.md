# Escenarios completos de compra

Guía de los flujos de compra de extremo a extremo a través del API Gateway.
Cada escenario usa `curl` + `jq` contra un stack Docker levantado
(`docker compose up -d --no-build`) y refleja exactamente lo que automatiza el
smoke E2E (`scripts/smoke-test.sh`). Es la documentación operativa de
[ADR-0012](./adr/ADR-0012-ci-y-smoke-test.md), [ADR-0013](./adr/ADR-0013-compensacion-stock-saga-outbox.md),
[ADR-0014](./adr/ADR-0014-contrato-http-pago-201-motivechazo.md) y
[ADR-0015](./adr/ADR-0015-arranque-ordenado-gateway-auth.md).

## Convenciones

| Tema | Valor |
|---|---|
| Gateway | `http://localhost:8080` |
| Autenticación | `JWT` en `Authorization: Bearer <token>` |
| Rutas públicas | `POST /api/v1/auth/register`, `POST /api/v1/auth/login` |
| Estado de pedidos | `CREADO` → `PAGADO` / `CANCELADO` |
| Estado de pagos | `PROCESADO` / `RECHAZADO` |
| Umbral de aprobación | `pagos.monto-maximo-aprobado` (por defecto `100.00`) |
| Rate limiting | `429` por usuario (Redis, ADR-0010); reintenta |
| Startup | el gateway espera `auth-service` y `redis` sanos (ADR-0015) |

Renombra/ahorra el token y los ids si quieres reproducirlo en una sola
sesión:

```bash
BASE=http://localhost:8080; EMAIL="compra-$(date +%s)@ecommerce.test"
api() { curl -s -o /tmp/body -w '%{http_code}' -X "$1" "$BASE$2" ${3:+-H "Authorization: Bearer $3"} ${4:+-H "Content-Type: application/json" -d "$4"} }
```

## Ruteo del gateway

| Prefijo | Servicio |
|---|---|
| `/api/v1/auth/**`, `/api/v1/usuario/**` | `auth-service` (:8081) |
| `/api/v1/producto/**`, `/api/v1/categoria/**` | `catalogo-service` (:8082) |
| `/api/v1/pedido/**` | `pedidos-service` (:8083) |
| `/api/v1/pago/**` | `pagos-service` (:8084) |
| `/api/v1/notificacion/**` | `notificaciones-service` (:8085) |

---

## Escenario 1 — Compra aprobada (happy path)

### 1.1 Registro

```bash
curl -s -X POST $BASE/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Compra Feliz","email":"'$EMAIL'","password":"Passw0rd!123"}'
```

```json
{ "userId": 9, "email": "compra-...@ecommerce.test", "rol": "USER", "token": "<jwt>" }
```

### 1.2 Login

```bash
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"'$EMAIL'","password":"Passw0rd!123"}' | jq -r '.token')
```

### 1.3 Promover a ADMIN (no existe admin inicial)

El rol se cambia en BD (mismo método que usa el smoke):

```bash
docker compose exec -T postgres psql -U ecommerce -d auth_db -v ON_ERROR_STOP=1 \
  -c "UPDATE usuario SET rol='ADMIN' WHERE email='$EMAIL';"
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"'$EMAIL'","password":"Passw0rd!123"}' | jq -r '.token')
```

### 1.4 Crear categoría y producto (ADMIN)

```bash
curl -s -X POST $BASE/api/v1/categoria -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"nombre":"Repostería","descripcion":"Dulces"}'
# -> 201 { "id": 1, ... }

curl -s -X POST $BASE/api/v1/producto -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Pastel de chocolate","descripcion":"1/8 de pastel","precio":30.00,"stock":50,"categoriaId":1}'
# -> 201 { "id": 1, ..., "precio": 30.00, "stock": 50, "estado": "ACTIVO" }
```

### 1.5 Crear pedido (descuenta stock atómico)

```bash
curl -s -X POST $BASE/api/v1/pedido -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"items":[{"productoId":1,"cantidad":3}]}'
```

```json
{ "id": 1, "usuarioId": 9, "total": 90.00, "estado": "CREADO", "items": [...],
  "fechaCreacion": "..." }
```

- El stock pasa de `50` a `47` en la misma transacción.
- Se publica `order-created` (outbox): el usuario recibe la notificación
  `Tu pedido #1 fue creado. Total: 90.0`.

### 1.6 Pago automático (aprobado)

`pagos-service` consume `order-created` y registra el pago. Los `90.00` están
por debajo del umbral `100.00` → `PROCESADO`. Un `POST /api/v1/pago` posterior
devuelve `409` (pedido ya pagado). El smoke espera el recurso con GET:

```bash
curl -s $BASE/api/v1/pago/pedido/1 -H "Authorization: Bearer $TOKEN"
```

```json
{ "id": 1, "pedidoId": 1, "usuarioId": 9, "monto": 90.00, "estado": "PROCESADO",
  "motivoRechazo": null, "intento": 1, "fechaProcesado": "..." }
```

Se publica `payment-processed`; al consumirlo, `pedidos-service` mueve el pedido
a `PAGADO` y `notificaciones-service` genera:
`El pago #1 del pedido #1 quedó en estado PROCESADO. Monto: 90.0`.

### 1.7 Verificar estados (eventual) y notificaciones

```bash
curl -s $BASE/api/v1/pedido/1 -H "Authorization: Bearer $TOKEN" | jq -r '.estado'   # PAGADO
curl -s $BASE/api/v1/pago/1 -H "Authorization: Bearer $TOKEN" | jq -r '.estado'    # PROCESADO
curl -s $BASE/api/v1/notificacion/mias -H "Authorization: Bearer $TOKEN" | jq -r '.[].mensaje'
```

### 1.8 Observabilidad

- **Zipkin** `http://localhost:9411` — una traza distribuye `gateway → auth →
  pedidos/pagos` con sus spans.
- **Kafka UI** `http://localhost:8089` — los tópicos muestran los mensajes de
  `order-created` y `payment-processed`.

---

## Escenario 2 — Pago rechazado y compensación de stock (saga, ADR-0013)

Parte por el mismo usuario ADMIN del Escenario 1, con el producto id `1`
(stock `47` tras el pedido feliz).

### 2.1 Crear pedido que supera el umbral

```bash
curl -s -X POST $BASE/api/v1/pedido -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"items":[{"productoId":1,"cantidad":4}]}'
# -> 201 { "id": 2, "total": 120.00, "estado": "CREADO", ... }
# stock: 47 -> 43
```

### 2.2 Pago automático (rechazado)

El mismo listener de `order-created` registra el pago. El total `120.00`
supera el umbral → `RECHAZADO`. El smoke espera con GET (no con POST):

```bash
curl -s $BASE/api/v1/pago/pedido/2 -H "Authorization: Bearer $TOKEN"
```

```json
{ "id": 2, "pedidoId": 2, "usuarioId": 9, "monto": 120.00, "estado": "RECHAZADO",
  "motivoRechazo": "Monto excede el máximo aprobado (100.00)", "intento": 1,
  "fechaProcesado": "..." }
```

> Un `POST /api/v1/pago` que sí crea un intento responde `201`: significa
> *recurso pago creado* (auditoría). El resultado de la aprobación se lee en
> `estado` + `motivoRechazo` (ADR-0014). Tras un pago automático, el POST
> choca con `409`. El POST se usa en el reintento tras `reactivar` (ADR-0016),
> porque esa transición no vuelve a publicar `order-created`.

### 2.3 Compensación (eventual, saga outbox)

1. `pagos-service` publica `payment-processed` con `estado=RECHAZADO`.
2. `pedidos-service` encola en su outbox un `RESTOCK_REQUIRED` por ítem y
   fija el pedido `CANCELADO` — misma transacción.
3. `OutboxPublisher` publica `restock-requested`; `catalogo-service` lo
   consume y repone el stock **exactamente una vez** (dedup por `eventId`
   = `pedidoId-productoId` en la tabla `restock_event`).

### 2.4 Verificar

```bash
curl -s $BASE/api/v1/pedido/2 -H "Authorization: Bearer $TOKEN" | jq -r '.estado'   # CANCELADO
curl -s $BASE/api/v1/pago/2 -H "Authorization: Bearer $TOKEN" | jq -r '.motivoRechazo'
curl -s $BASE/api/v1/producto/1 | jq -r '.stock'                                    # 47 (restaurado)
docker compose exec -T postgres psql -U ecommerce -d catalogo_db -c \
  "SELECT event_id, pedido_id, producto_id, cantidad FROM restock_event;"
# 1 fila: "2-1", 2, 1, 4  -> compensación aplicada una sola vez
```

La notificación del rechazo incluye el motivo:
`El pago #2 del pedido #2 quedó en estado RECHAZADO. Monto: 120.0
Motivo: Monto excede el máximo aprobado (100.0)`.

---

## Escenario 3 — Cancelación manual de un pedido sin pagar

Un pedido en `CREADO` puede cancelarse por su dueño; la compensación de stock
usa el mismo mecanismo de outbox del Escenario 2.

```bash
curl -s -X PUT $BASE/api/v1/pedido/3/cancelar -H "Authorization: Bearer $TOKEN"
# -> 200 { ..., "estado": "CANCELADO" }
# stock restaurado a su valor previo (compensación outbox)
```

Regla: solo un pedido `CREADO` se puede cancelar. Después de `PAGADO` no hay
cancelación (follow-up abierto).

---

## Escenario 4 — Reproducción de errores típicos

| Acción | Código | Motivo |
|---|---|---|
| `POST /pago` para un pedido ya pagado/rechazado | `409` | unicidad `pedido_id`; **sin reintento aún** (ADR-0014, follow-up) |
| Llamadas sin `Authorization` | `401` | JWT requerido (gateway) |
| `POST /producto` con rol `USER` | `403` | operación de `ADMIN` |
| Más de N peticiones por usuario/instante | `429` | rate limiting Redis (ADR-0010); reintenta |
| Id inexistente | `404` | `ResourceNotFoundException` |

---

## Relación con el smoke E2E y los ADR

- El **smoke E2E** automatiza los Escenarios 1 y 2 (partes 1-8 de
  `scripts/smoke-test.sh`) contra las imágenes publicadas en GHCR.
- **ADR-0012** — CI y smoke; **ADR-0013** — compensación por saga+outbox;
  **ADR-0014** — contrato 201 y `motivoRechazo`; **ADR-0015** — arranque
  ordenado de gateway.
- Los escenarios 3 y 4 documentan el comportamiento existente y los límites
  conocidos (cancelación post-pago y reintento de pago quedan como follow-ups
  en la hoja de ruta).