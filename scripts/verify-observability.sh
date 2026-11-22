#!/usr/bin/env bash
# Verifies the local observability stack after scripts/smoke-test.sh has run:
#   metrics in Prometheus, logs (with trace ids) in Loki, and one trace spanning
#   gateway -> order-service -> Kafka -> payment-service -> Kafka -> notification-service in Tempo.
#
# Usage: scripts/verify-observability.sh [TIMEOUT_SECONDS]
set -euo pipefail

PROM=${PROM_URL:-http://localhost:9090}
LOKI=${LOKI_URL:-http://localhost:3100}
TEMPO=${TEMPO_URL:-http://localhost:3200}
TIMEOUT=${1:-120}
deadline=$(( $(date +%s) + TIMEOUT ))
SERVICES=(api-gateway user-service order-service payment-service notification-service)

fail() { echo "FAIL: $*" >&2; exit 1; }
until_ok() { # desc, command...
  local desc=$1; shift
  while (( $(date +%s) < deadline )); do
    if out=$("$@" 2>/dev/null) && [[ -n "$out" ]]; then printf '  %-55s %s\n' "$desc" "$out"; return 0; fi
    sleep 5
  done
  fail "timed out: $desc"
}

prom_value() { # promql -> prints value if > 0
  curl -fsS -G "$PROM/api/v1/query" --data-urlencode "query=$1" | jq -er '.data.result[0].value[1] | select(tonumber > 0)'
}

echo "==> Prometheus (metrics)"
for s in "${SERVICES[@]}"; do until_ok "up{job=\"$s\"}" prom_value "up{job=\"$s\"}"; done
until_ok "orders_placed_total"                       prom_value 'sum(orders_placed_total{job="order-service"})'
until_ok "payments_processed_total{status=APPROVED}" prom_value 'sum(payments_processed_total{job="payment-service",status="APPROVED"})'
until_ok "events_published (order-service)"         prom_value 'sum(events_published_seconds_count{job="order-service",outcome="success"})'
until_ok "events_consumed (notification-service)"   prom_value 'sum(events_consumed_seconds_count{job="notification-service",outcome="success"})'
until_ok "events_consume_lag recorded"              prom_value 'sum(events_consume_lag_seconds_count{job="payment-service"})'
until_ok "gateway route metrics"                    prom_value 'sum(spring_cloud_gateway_requests_seconds_count{job="api-gateway",routeId="order-service"})'

echo "==> Loki (logs)"
loki_count() { # logql selector -> number of lines in the last 15 min
  curl -fsS -G "$LOKI/loki/api/v1/query_range" --data-urlencode "query=$1" \
    --data-urlencode "start=$(( ($(date +%s) - 900) * 1000000000 ))" --data-urlencode "limit=100" \
  | jq -er '[.data.result[].values | length] | add | select(. > 0)'
}
for s in "${SERVICES[@]}"; do until_ok "{service_name=\"$s\"}" loki_count "{service_name=\"$s\"}"; done
until_ok "payment-service logs carrying trace_id" loki_count '{service_name="payment-service"} | trace_id != ""'

echo "==> Tempo (traces)"
trace_services() { # newest trace that reached notification-service -> its distinct services
  local id
  id=$(curl -fsS -G "$TEMPO/api/search" --data-urlencode 'q={resource.service.name="notification-service"}' \
         --data-urlencode limit=20 | jq -er '[.traces[]] | sort_by(.startTimeUnixNano) | last | .traceID')
  curl -fsS "$TEMPO/api/traces/$id" | jq -er '
    [(.batches // .resourceSpans)[] | .resource.attributes[] | select(.key=="service.name") | .value.stringValue]
    | unique | select(length >= 4) | join(",")'
}
until_ok "one trace spans" trace_services
services=$(trace_services)
for s in api-gateway order-service payment-service notification-service; do
  [[ ",$services," == *",$s,"* ]] || fail "trace is missing $s (got: $services)"
done

echo; echo "OBSERVABILITY CHECK PASSED"
