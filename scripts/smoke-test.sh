#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# Smoke test E2E del ecommerce a traves del API Gateway.
#
# Escenario: registro -> login -> (promover a ADMIN via psql, no hay admin
# inicial) -> categoria -> producto -> pedido (descuenta stock, publica
# order-created) -> pago (publica payment-processed) -> pedido PAGADO ->
# notificacion creada -> traza visible en Zipkin.
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
# PRECIO y CANTIDAD elegidos para que total (3 * 30.00 = 90.00) quede por debajo
# de pagos.monto-maximo-aprobado (100.00 por defecto); si lo supera, el pago
# se emite como RECHAZADO y el pedido pasa a CANCELADO.
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
echo "[1/7] Esperando al API Gateway ($BASE_URL)..."
wait_until "gateway /actuator/health" 120 gateway_ready

# --- 2. Registro y login ----------------------------------------------------
echo "[2/7] Registro y login..."
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
echo "[3/7] Promover usuario a ADMIN (bootstrap via psql)..."
docker compose exec -T postgres psql -U ecommerce -d auth_db -v ON_ERROR_STOP=1 \
  -c "UPDATE usuario SET rol='ADMIN' WHERE email='$EMAIL';" >/dev/null \
  || fail "no se pudo actualizar el rol en auth_db"
code=$(api POST /api/v1/auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
expect 200 "$code"
[ "$(jq -r '.rol' "$BODY_TMP")" = "ADMIN" ] || fail "rol ADMIN esperado tras la promocion"
TOKEN=$(jq -r '.token' "$BODY_TMP")
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || fail "token no emitido"

# --- 4. Categoria y producto ------------------------------------------------
echo "[4/7] Crear categoria y producto (admin)..."
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
echo "[5/7] Crear pedido y verificar descuento de stock..."
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
echo "[6/7] Procesar pago y verificar estados tras los eventos Kafka..."
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

# --- 7. Trazabilidad distribuida --------------------------------------------
echo "[7/7] Verificar traza distribuida en Zipkin..."
traces_ok() {
  local n
  n=$(curl -s "$ZIPKIN_URL/api/v2/traces?limit=5" | jq 'length' 2>/dev/null || true)
  [ "$n" -ge 1 ] 2>/dev/null
}
wait_until "traza en Zipkin" 120 traces_ok

echo
echo "SMOKE OK: pedido $PEDIDO_ID (total $TOTAL), pago $PAGO_ID, estado PAGADO,"
echo "stock y notificaciones verificados, traza distribuida en Zipkin."