#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# Smoke test E2E del ecommerce a traves del API Gateway.
#
# Escenario: registro -> login -> (promover a ADMIN via psql, no hay admin
# inicial) -> categoria -> producto -> pedido (descuenta stock, publica
# order-created) -> pago (publica payment-processed) -> pedido PAGADO ->
# notificacion creada -> traza visible en Zipkin.
# Ademas valida la saga de compensacion (ADR-0013): el pago AUTOMATICO de un
# segundo pedido (disparado por order-created) es RECHAZADO porque su total
# supera pagos.monto-maximo-aprobado -> pedido CANCELADO -> la compensacion
# (outbox RESTOCK_REQUIRED) restaura el stock en catalogo.
# Y valida el reintento de pago (ADR-0016): reactivar -> re-reserva stock ->
# subir el umbral aprobado en runtime (tabla configuracion) -> nuevo pago
# PROCESADO (intento 2) -> pedido PAGADO sin re-publicar order-created.
#
# Pre-requisitos:
#   - Stack arriba con las imagenes publicadas:
#       docker compose pull <servicios>
#       docker compose up -d --no-build <servicios>
#   - curl y jq disponibles en el runner.
# =============================================================================

BASE_URL="${BASE_URL:-http://localhost:8080}"
ZIPKIN_URL="${ZIPKIN_URL:-http://localhost:9411}"

EMAIL="smoke-$(date +%s)@ecommerce.test"
PASSWORD="SmokePass123!"
SUFIJO_UNICO="$(date +%s)-$RANDOM"
STOCK_INICIAL=50
CANTIDAD=3
# CANTIDAD_RECHAZO elegida para que el total (4 * 30.00 = 120.00) supere
# pagos.monto-maximo-aprobado (100.00 por defecto) y el pago automatico salga
# RECHAZADO, disparando la saga de compensacion (pedido CANCELADO + restock).
CANTIDAD_RECHAZO=4
# PRECIO y CANTIDAD del pedido feliz elegidos para que total (3 * 30.00 = 90.00)
# quede por debajo de pagos.monto-maximo-aprobado (100.00 por defecto); si lo
# supera, el pago se emite como RECHAZADO y el pedido pasa a CANCELADO.
PRECIO=30.00

BODY_TMP="$(mktemp)"
trap 'rm -f "$BODY_TMP"' EXIT

fail() { echo "SMOKE FALLIDO: $*" >&2; exit 1; }

api() { # api METODO PATH [DATA] [TOKEN]  -> imprime el codigo HTTP, cuerpo en $BODY_TMP
  local method="$1" path="$2" data="${3:-}" token="${4:-}"
  local -a args=(-s -o "$BODY_TMP" -w '%{http_code}' -X "$method" "$BASE_URL$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-H "Content-Type: application/json" -d "$data")
  curl "${args[@]}"
}

expect() { [ "$1" = "$2" ] || fail "HTTP esperado $1, obtenido $2 :: $(cat "$BODY_TMP")"; }

expect_retryable() { # para usar dentro de wait_until: devuelve 1 (reintenta) en vez de abortar
  [ "$1" = "$2" ] || { echo "  retry: HTTP esperado $1, obtenido $2" >&2; return 1; }
}

expect_retryable_srv() { # como expect_retryable, pero los 5xx (servicio aun arrancando) reintentan
  case "$2" in
    "$1") return 0 ;;
    500|502|503|504) return 1 ;;
    *) fail "HTTP esperado $1, obtenido $2 :: $(cat "$BODY_TMP")" ;;
  esac
}

wait_until() { # wait_until DESCRIPCION SEGUNDOS comando...
  local desc="$1" timeout="$2"
  shift 2
  local i=0
  until "$@"; do
    i=$((i + 1))
    [ "$i" -ge "$timeout" ] && fail "timeout esperando: $desc"
    sleep 1
  done
  echo "  OK: $desc"
}

gateway_ready() { [ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/actuator/health")" = "200" ]; }

# --- 1. Gateway listo -------------------------------------------------------
echo "[1/9] Esperando al API Gateway ($BASE_URL)..."
wait_until "gateway /actuator/health" 120 gateway_ready

# --- 2. Registro y login ----------------------------------------------------
echo "[2/9] Registro y login..."
# El gateway puede estar listo antes que auth-service; por eso los 5xx se
# reintentan (sin registrar el 500 como fallo).
registrar() {
  local code
  code=$(api POST /api/v1/auth/register "{\"nombre\":\"Smoke User\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
  expect_retryable_srv 201 "$code"
}
wait_until "registrar usuario" 120 registrar

login_ok() {
  local code
  code=$(api POST /api/v1/auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
  expect_retryable_srv 200 "$code"
  [ "$(jq -r '.rol' "$BODY_TMP")" = "USER" ] || fail "rol inicial esperado USER"
}
wait_until "login" 60 login_ok

# --- 3. Promover a ADMIN (no existe admin inicial) --------------------------
echo "[3/9] Promover usuario a ADMIN (bootstrap via psql)..."
docker compose exec -T postgres psql -U ecommerce -d auth_db -v ON_ERROR_STOP=1 \
  -c "UPDATE usuario SET rol='ADMIN' WHERE email='$EMAIL';" >/dev/null \
  || fail "no se pudo actualizar el rol en auth_db"
code=$(api POST /api/v1/auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
expect 200 "$code"
[ "$(jq -r '.rol' "$BODY_TMP")" = "ADMIN" ] || fail "rol ADMIN esperado tras la promocion"
TOKEN=$(jq -r '.token' "$BODY_TMP")
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || fail "token no emitido"

# --- 4. Categoria y producto ------------------------------------------------
echo "[4/9] Crear categoria y producto (admin)..."
crear_categoria() {
  local code
  code=$(api POST /api/v1/categoria '{"nombre":"Smoke Categoria","descripcion":"creada por smoke"}' "$TOKEN")
  expect_retryable 201 "$code"
  CATEGORIA_ID=$(jq -r '.id' "$BODY_TMP")
  [ -n "$CATEGORIA_ID" ] && [ "$CATEGORIA_ID" != "null" ]
}
wait_until "crear categoria" 60 crear_categoria
echo "  categoria: $CATEGORIA_ID"

crear_producto() {
  local code
  code=$(api POST /api/v1/producto \
    "{\"nombre\":\"Smoke Producto $SUFIJO_UNICO\",\"descripcion\":\"creado por smoke\",\"precio\":$PRECIO,\"stock\":$STOCK_INICIAL,\"categoriaId\":$CATEGORIA_ID}" \
    "$TOKEN")
  expect_retryable 201 "$code"
  PRODUCTO_ID=$(jq -r '.id' "$BODY_TMP")
  [ -n "$PRODUCTO_ID" ] && [ "$PRODUCTO_ID" != "null" ]
}
wait_until "crear producto" 60 crear_producto
echo "  producto: $PRODUCTO_ID"

# --- 5. Pedido y descuento de stock -----------------------------------------
echo "[5/9] Crear pedido y verificar descuento de stock..."
crear_pedido() {
  local code
  code=$(api POST /api/v1/pedido "{\"items\":[{\"productoId\":$PRODUCTO_ID,\"cantidad\":$CANTIDAD}]}" "$TOKEN")
  expect_retryable 201 "$code"
  PEDIDO_ID=$(jq -r '.id' "$BODY_TMP")
  TOTAL=$(jq -r '.total' "$BODY_TMP")
  [ -n "$PEDIDO_ID" ] && [ "$PEDIDO_ID" != "null" ] && [ -n "$TOTAL" ] && [ "$TOTAL" != "null" ]
}
wait_until "crear pedido" 60 crear_pedido
echo "  pedido: $PEDIDO_ID (total $TOTAL)"

stock_ok() {
  local code stock
  code=$(api GET "/api/v1/producto/$PRODUCTO_ID" "" "$TOKEN")
  expect_retryable 200 "$code"
  stock=$(jq -r '.stock' "$BODY_TMP" 2>/dev/null || true)
  [ "$stock" = "$((STOCK_INICIAL - CANTIDAD))" ]
}
wait_until "stock descontado a $((STOCK_INICIAL - CANTIDAD))" 30 stock_ok

# --- 6. Pago y cadena Kafka -------------------------------------------------
echo "[6/9] Procesar pago y verificar estados tras los eventos Kafka..."
pagar() {
  local code
  code=$(api POST /api/v1/pago "{\"pedidoId\":$PEDIDO_ID,\"monto\":$TOTAL}" "$TOKEN")
  expect_retryable 201 "$code"
  PAGO_ID=$(jq -r '.id' "$BODY_TMP")
  [ -n "$PAGO_ID" ] && [ "$PAGO_ID" != "null" ]
}
wait_until "procesar pago" 60 pagar
echo "  pago: $PAGO_ID"

pago_procesado() {
  local code
  code=$(api GET "/api/v1/pago/$PAGO_ID" "" "$TOKEN")
  expect_retryable 200 "$code"
  [ "$(jq -r '.estado' "$BODY_TMP")" = "PROCESADO" ]
}
wait_until "pago en estado PROCESADO" 60 pago_procesado

pedido_pagado() {
  local code
  code=$(api GET "/api/v1/pedido/$PEDIDO_ID" "" "$TOKEN")
  expect_retryable 200 "$code"
  [ "$(jq -r '.estado' "$BODY_TMP")" = "PAGADO" ]
}
wait_until "pedido en estado PAGADO" 90 pedido_pagado

notificacion_creada() {
  local code n
  code=$(api GET /api/v1/notificacion/mias "" "$TOKEN")
  expect_retryable 200 "$code" || return 1
  n=$(jq 'length' "$BODY_TMP" 2>/dev/null || true)
  [ "$n" -ge 1 ] 2>/dev/null || return 1
}
wait_until "al menos una notificacion para el usuario" 90 notificacion_creada

# --- 7. Saga de compensacion: pago automatico RECHAZADO -> CANCELADO -> restock
echo "[7/9] Verificar compensacion de stock por rechazo de pago..."
STOCK_TRAS_CANCELACION=$((STOCK_INICIAL - CANTIDAD - CANTIDAD_RECHAZO))

crear_pedido_rechazado() {
  local code
  code=$(api POST /api/v1/pedido "{\"items\":[{\"productoId\":$PRODUCTO_ID,\"cantidad\":$CANTIDAD_RECHAZO}]}" "$TOKEN")
  expect_retryable 201 "$code"
  PEDIDO_RECHAZADO_ID=$(jq -r '.id' "$BODY_TMP")
  TOTAL_RECHAZADO=$(jq -r '.total' "$BODY_TMP")
  [ -n "$PEDIDO_RECHAZADO_ID" ] && [ "$PEDIDO_RECHAZADO_ID" != "null" ] \
    && [ -n "$TOTAL_RECHAZADO" ] && [ "$TOTAL_RECHAZADO" != "null" ]
}
wait_until "crear pedido que sera rechazado" 60 crear_pedido_rechazado
echo "  pedido a rechazar: $PEDIDO_RECHAZADO_ID (total $TOTAL_RECHAZADO)"

stock_reservado_rechazado() {
  local code stock
  code=$(api GET "/api/v1/producto/$PRODUCTO_ID" "" "$TOKEN")
  expect_retryable 200 "$code"
  stock=$(jq -r '.stock' "$BODY_TMP" 2>/dev/null || true)
  [ "$stock" = "$STOCK_TRAS_CANCELACION" ]
}
wait_until "stock reservado por el pedido a $STOCK_TRAS_CANCELACION" 30 stock_reservado_rechazado

# El pago AUTOMATICO (disparado por order-created en pagos) queda RECHAZADO
# porque el total (120.00) supera pagos.monto-maximo-aprobado (100.00).
pago_auto_rechazado() {
  local code
  code=$(api GET "/api/v1/pago/pedido/$PEDIDO_RECHAZADO_ID" "" "$TOKEN")
  expect_retryable 200 "$code" || return 1
  [ "$(jq -r '.estado' "$BODY_TMP")" = "RECHAZADO" ] \
    && [ "$(jq -r '.intento' "$BODY_TMP")" = "1" ] || return 1
  PAGO_RECHAZADO_ID=$(jq -r '.id' "$BODY_TMP")
}
wait_until "pago automatico en estado RECHAZADO (intento 1)" 90 pago_auto_rechazado
echo "  pago rechazado (automatico): $PAGO_RECHAZADO_ID"

pedido_cancelado() {
  local code
  code=$(api GET "/api/v1/pedido/$PEDIDO_RECHAZADO_ID" "" "$TOKEN")
  expect_retryable 200 "$code"
  [ "$(jq -r '.estado' "$BODY_TMP")" = "CANCELADO" ] \
    && [ "$(jq -r '.motivoCancelacion' "$BODY_TMP")" = "PAGO_RECHAZADO" ]
}
wait_until "pedido en estado CANCELADO (PAGO_RECHAZADO)" 90 pedido_cancelado

# La saga de compensacion (outbox RESTOCK_REQUIRED -> catalogo) restaura el stock.
stock_restaurado() {
  local code stock
  code=$(api GET "/api/v1/producto/$PRODUCTO_ID" "" "$TOKEN")
  expect_retryable 200 "$code"
  stock=$(jq -r '.stock' "$BODY_TMP" 2>/dev/null || true)
  [ "$stock" = "$((STOCK_INICIAL - CANTIDAD))" ]
}
wait_until "stock restaurado a $((STOCK_INICIAL - CANTIDAD))" 90 stock_restaurado
echo "  restock aplicado (stock de vuelta a $((STOCK_INICIAL - CANTIDAD)))"

# --- 8. Reintento de pago tras rechazo (ADR-0016) ---------------------------
echo "[8/9] Reintento de pago: reactivar, re-reservar stock y pagar de nuevo..."
reactivar_pedido() {
  local code
  code=$(api PUT "/api/v1/pedido/$PEDIDO_RECHAZADO_ID/reactivar" "" "$TOKEN")
  expect_retryable 200 "$code"
  [ "$(jq -r '.estado' "$BODY_TMP")" = "CREADO" ] \
    && [ "$(jq -r '.motivoCancelacion' "$BODY_TMP")" = "null" ]
}
wait_until "reactivar pedido (CREADO)" 60 reactivar_pedido
echo "  pedido reactivado a CREADO"

stock_reactivado() {
  local code stock
  code=$(api GET "/api/v1/producto/$PRODUCTO_ID" "" "$TOKEN")
  expect_retryable 200 "$code"
  stock=$(jq -r '.stock' "$BODY_TMP" 2>/dev/null || true)
  [ "$stock" = "$STOCK_TRAS_CANCELACION" ]
}
wait_until "stock re-reservado a $STOCK_TRAS_CANCELACION" 60 stock_reactivado
echo "  stock re-reservado a $STOCK_TRAS_CANCELACION"

# El umbral de aprobacion se lee en cada pago (tabla configuracion); se sube en
# runtime para que el reintento (mismo monto 120.00) salga PROCESADO.
docker compose exec -T postgres psql -U ecommerce -d pagos_db -v ON_ERROR_STOP=1 \
  -c "INSERT INTO configuracion (clave,valor) VALUES ('monto_maximo_aprobado','10000') \
      ON CONFLICT (clave) DO UPDATE SET valor='10000';" >/dev/null \
  || fail "no se pudo ajustar el umbral de aprobacion en pagos_db"

pagar_reintento() {
  local code
  code=$(api POST /api/v1/pago "{\"pedidoId\":$PEDIDO_RECHAZADO_ID,\"monto\":$TOTAL_RECHAZADO}" "$TOKEN")
  expect_retryable 201 "$code"
  [ "$(jq -r '.intento' "$BODY_TMP")" = "2" ] \
    && [ "$(jq -r '.estado' "$BODY_TMP")" = "PROCESADO" ] || return 1
}
wait_until "pago de reintento (intento 2) en estado PROCESADO" 60 pagar_reintento
echo "  pago reintentado (intento 2) PROCESADO"

pedido_pagado_despues_de_reintentar() {
  local code
  code=$(api GET "/api/v1/pedido/$PEDIDO_RECHAZADO_ID" "" "$TOKEN")
  expect_retryable 200 "$code"
  [ "$(jq -r '.estado' "$BODY_TMP")" = "PAGADO" ]
}
wait_until "pedido PAGADO tras el reintento" 90 pedido_pagado_despues_de_reintentar

# El pedido pagado conserva su reserva: el stock no vuelve a liberarse.
stock_final_reintento() {
  local code stock
  code=$(api GET "/api/v1/producto/$PRODUCTO_ID" "" "$TOKEN")
  expect_retryable 200 "$code"
  stock=$(jq -r '.stock' "$BODY_TMP" 2>/dev/null || true)
  [ "$stock" = "$STOCK_TRAS_CANCELACION" ]
}
wait_until "stock sin liberar tras el pago (se mantiene $STOCK_TRAS_CANCELACION)" 60 stock_final_reintento
echo "  stock correcto tras el pago (reserva conservada a $STOCK_TRAS_CANCELACION)"

# --- 9. Trazabilidad distribuida --------------------------------------------
echo "[9/9] Verificar traza distribuida en Zipkin..."
traces_ok() {
  local n
  n=$(curl -s "$ZIPKIN_URL/api/v2/traces?limit=5" | jq 'length' 2>/dev/null || true)
  [ "$n" -ge 1 ] 2>/dev/null
}
wait_until "traza en Zipkin" 120 traces_ok

echo
echo "SMOKE OK: pedido $PEDIDO_ID (total $TOTAL) PAGADO, pago $PAGO_ID PROCESADO,"
echo "compensacion verificada (pago $PAGO_RECHAZADO_ID RECHAZADO -> pedido CANCELADO"
echo "-> stock restaurado), reintento verificado (reactivar -> pago intento 2"
echo "PROCESADO -> pedido PAGADO con reserva conservada), notificaciones y traza"
echo "distribuida en Zipkin."