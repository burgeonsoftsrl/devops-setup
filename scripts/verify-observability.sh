#!/usr/bin/env bash
# Verifies the local observability stack after scripts/smoke-test.sh has run:
#   metrics in Prometheus, logs (with trace ids) in Loki, and one trace spanning
#   gateway -> order-service -> Kafka -> payment-service -> Kafka -> notification-service in Tempo.
#
# Usage: scripts/verify-observability.sh [TIMEOUT_SECONDS]
#   local stack (default):  Prometheus :9090, Loki :3100, Tempo :3200
#   Grafana Cloud:          GRAFANA_URL=https://<stack>.grafana.net GRAFANA_SA_TOKEN=glsa_... \
#                           scripts/verify-observability.sh 300
#                           (queries go through Grafana's datasource proxy; optional
#                            PROM_UID / LOKI_UID / TEMPO_UID, default grafanacloud-{prom,logs,traces})
set -euo pipefail

CURL=(curl -fsS -m 60)
if [[ -n "${GRAFANA_URL:-}" ]]; then
  [[ -n "${GRAFANA_SA_TOKEN:-}" ]] || { echo "GRAFANA_SA_TOKEN is required with GRAFANA_URL" >&2; exit 2; }
  CURL+=(-H "Authorization: Bearer $GRAFANA_SA_TOKEN")
  proxy="${GRAFANA_URL%/}/api/datasources/proxy/uid"
  PROM="$proxy/${PROM_UID:-grafanacloud-prom}"
  LOKI="$proxy/${LOKI_UID:-grafanacloud-logs}"
  TEMPO="$proxy/${TEMPO_UID:-grafanacloud-traces}"
  MODE=cloud
else
  PROM=${PROM_URL:-http://localhost:9090}
  LOKI=${LOKI_URL:-http://localhost:3100}
  TEMPO=${TEMPO_URL:-http://localhost:3200}
  MODE=local
fi
TIMEOUT=${1:-120}
deadline=$(( $(date +%s) + TIMEOUT ))
SERVICES=(api-gateway user-service order-service payment-service notification-service)

fail() { echo "FAIL: $*" >&2; [[ "${IN_TRACES:-}" == 1 ]] && tempo_debug; exit 1; }
until_ok() { # desc, command...
  local desc=$1; shift
  while (( $(date +%s) < deadline )); do
    if out=$("$@" 2>/dev/null) && [[ -n "$out" ]]; then printf '  %-55s %s\n' "$desc" "$out"; return 0; fi
    sleep 5
  done
  fail "timed out: $desc"
}

prom_value() { # promql -> prints value if > 0
  "${CURL[@]}" -G "$PROM/api/v1/query" --data-urlencode "query=$1" | jq -er '.data.result[0].value[1] | select(tonumber > 0)'
}

echo "==> Metrics ($MODE: $PROM)"
for s in "${SERVICES[@]}"; do
  if [[ $MODE == local ]]; then until_ok "up{job=\"$s\"}" prom_value "up{job=\"$s\"}"
  else until_ok "jvm metrics pushed (job=\"$s\")" prom_value "count(jvm_memory_used_bytes{job=\"$s\"})"; fi
done
until_ok "orders_placed_total"                       prom_value 'sum(orders_placed_total{job="order-service"})'
until_ok "payments_processed_total{status=APPROVED}" prom_value 'sum(payments_processed_total{job="payment-service",status="APPROVED"})'
until_ok "events_published (order-service)"         prom_value 'sum(events_published_seconds_count{job="order-service",outcome="success"})'
until_ok "events_consumed (notification-service)"   prom_value 'sum(events_consumed_seconds_count{job="notification-service",outcome="success"})'
until_ok "events_consume_lag recorded"              prom_value 'sum(events_consume_lag_seconds_count{job="payment-service"})'
until_ok "gateway route metrics"                    prom_value 'sum(spring_cloud_gateway_requests_seconds_count{job="api-gateway",routeId="order-service"})'

echo "==> Logs ($MODE)"
loki_count() { # logql selector -> number of lines in the last 15 min
  "${CURL[@]}" -G "$LOKI/loki/api/v1/query_range" --data-urlencode "query=$1" \
    --data-urlencode "start=$(( ($(date +%s) - 900) * 1000000000 ))" --data-urlencode "limit=100" \
  | jq -er '[.data.result[].values | length] | add | select(. > 0)'
}
for s in "${SERVICES[@]}"; do until_ok "{service_name=\"$s\"}" loki_count "{service_name=\"$s\"}"; done
until_ok "payment-service logs carrying trace_id" loki_count '{service_name="payment-service"} | trace_id != ""'

echo "==> Traces ($MODE)"
IN_TRACES=1
trace_services() { # newest trace that reached notification-service -> its distinct services
  local id
  # the POST /api/orders trace: gateway -> order-service -> Kafka -> payment-service -> Kafka -> notification-service
  id=$("${CURL[@]}" -G "$TEMPO/api/search" \
         --data-urlencode 'q={resource.service.name="api-gateway"} && {resource.service.name="order-service"} && {resource.service.name="payment-service" && kind=consumer} && {resource.service.name="notification-service" && kind=consumer}' \
         --data-urlencode limit=20 | jq -er '[.traces[]] | sort_by(.startTimeUnixNano) | last | .traceID')
  "${CURL[@]}" "$TEMPO/api/traces/$id" | jq -er '
    [(.batches // .resourceSpans)[] | .resource.attributes[] | select(.key=="service.name") | .value.stringValue]
    | unique | select(length >= 4) | join(",")'
}
tempo_debug() {
  echo "--- Tempo diagnostics"
  for s in "${SERVICES[@]}"; do
    printf '  %-22s ' "$s"
    "${CURL[@]}" -G "$TEMPO/api/search" --data-urlencode "q={resource.service.name=\"$s\"}" --data-urlencode limit=50 \
      | jq -c '{traces: (.traces | length), roots: ([.traces[].rootServiceName] | unique), names: ([.traces[].rootTraceName] | unique | .[:6])}' \
      || echo "search failed"
  done
  id=$("${CURL[@]}" -G "$TEMPO/api/search" --data-urlencode 'q={resource.service.name="notification-service"}' --data-urlencode limit=20 \
       | jq -r '[.traces[]] | sort_by(.startTimeUnixNano) | last | .traceID // empty')
  [[ -n "$id" ]] && "${CURL[@]}" "$TEMPO/api/traces/$id" | jq -c '{keys: keys, services: [(.batches // .resourceSpans // [])[] | .resource.attributes[]? | select(.key=="service.name") | .value.stringValue] | unique}'
}
until_ok "one trace spans" trace_services
services=$(trace_services)
for s in api-gateway order-service payment-service notification-service; do
  [[ ",$services," == *",$s,"* ]] || fail "trace is missing $s (got: $services)"
done

echo; echo "OBSERVABILITY CHECK PASSED"
