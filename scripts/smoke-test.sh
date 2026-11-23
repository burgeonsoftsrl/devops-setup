#!/usr/bin/env bash
# End-to-end smoke test through the API gateway:
#   user -> order (Mongo) -> OrderCreated (Kafka) -> payment (Postgres) -> PaymentProcessed (Kafka)
#   -> 2 notifications (Mongo)
#
# Usage: scripts/smoke-test.sh [GATEWAY_URL] [TIMEOUT_SECONDS]
#   scripts/smoke-test.sh                                         # local (compose/Tilt)
#   scripts/smoke-test.sh https://api-gateway-xxxx.onrender.com 300   # prod (cold starts)
set -euo pipefail

GW="${1:-http://localhost:8080}"; GW="${GW%/}"
TIMEOUT="${2:-120}"
start=$(date +%s)
deadline=$(( start + TIMEOUT ))

step() { printf '\n==> %s\n' "$*"; }
fail() { echo "FAIL: $*" >&2; exit 1; }
remaining() { echo $(( deadline - $(date +%s) )); }

# Poll until the command prints a non-empty, non-"null" value.
wait_for() {
  local desc="$1"; shift
  local out
  while (( $(remaining) > 0 )); do
    out=$("$@" 2>/dev/null || true)
    if [[ -n "$out" && "$out" != "null" ]]; then echo "$out"; return 0; fi
    sleep 3
  done
  fail "timed out waiting for: $desc"
}

http() { curl -fsS -m 60 "$@"; }

step "Waiting for every service to be UP via $GW (timeout ${TIMEOUT}s)"
wait_for "gateway health" sh -c "curl -fsS -m 60 '$GW/actuator/health' | jq -er 'select(.status==\"UP\") | .status'" >/dev/null
echo "  gateway UP"
# Hit every backend route in parallel: on free tiers each sleeping instance needs ~2 min to wake
# (and its Kafka consumers only start once it's up), so waking them one by one doesn't fit.
pids=()
for path in users orders payments notifications; do
  ( wait_for "/api/$path" sh -c "curl -fsS -m 60 -o /dev/null -w '%{http_code}' '$GW/api/$path' | grep -x 200" >/dev/null \
      && echo "  /api/$path OK after $(( $(date +%s) - start ))s" ) &
  pids+=($!)
done
for pid in "${pids[@]}"; do wait "$pid" || fail "a backend did not become ready in time"; done

step "Creating user"
email="smoke-$(date +%s)-$RANDOM@example.com"
user=$(http -X POST "$GW/api/users" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Smoke Test\",\"email\":\"$email\"}")
user_id=$(jq -er .id <<<"$user"); echo "  user #$user_id"

step "Creating order"
order=$(http -X POST "$GW/api/orders" -H 'Content-Type: application/json' \
  -d "{\"userId\":$user_id,\"product\":\"Smoke Book\",\"quantity\":2,\"price\":9.99}")
order_id=$(jq -er .id <<<"$order"); echo "  order $order_id"

step "Waiting for payment-service to consume OrderCreated"
status=$(wait_for "payment for $order_id" sh -c "curl -fsS -m 60 '$GW/api/payments?orderId=$order_id' | jq -er '.[0].status'")
echo "  payment status: $status"
[[ "$status" == "APPROVED" ]] || fail "expected APPROVED, got $status"

step "Waiting for notification-service to store both events"
types=$(wait_for "2 notifications for $order_id" sh -c \
  "curl -fsS -m 60 '$GW/api/notifications?orderId=$order_id' | jq -er 'select(length>=2) | map(.type) | sort | join(\",\")'")
echo "  notifications: $types"
[[ "$types" == "ORDER_CREATED,PAYMENT_PROCESSED" ]] || fail "unexpected notifications: $types"

printf '\nSMOKE TEST PASSED (order %s)\n' "$order_id"
