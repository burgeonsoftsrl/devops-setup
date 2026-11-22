#!/usr/bin/env python3
"""Generates one Grafana dashboard per service into this directory.

The dashboards are portable: datasources are template variables (Prometheus, Loki, Tempo), and
metric names/labels are identical whether Prometheus scrapes /actuator/prometheus (local, job=<service>)
or services push OTLP to Grafana Cloud (job=<service.name>, base time unit = seconds).

Usage: python3 observability/dashboards/generate.py   (re-run after editing)
"""
import json
from pathlib import Path

OUT = Path(__file__).parent

SERVICES = {
    "api-gateway": "API gateway: routing to the 4 backends",
    "user-service": "Users (Postgres)",
    "order-service": "Orders (MongoDB) -> publishes OrderCreated",
    "payment-service": "Payments (Postgres) <- OrderCreated, -> PaymentProcessed",
    "notification-service": "Notifications (MongoDB) <- OrderCreated, PaymentProcessed",
}

RATE = "$__rate_interval"


def sel(svc, extra=""):
    return f'job="{svc}"' + (f",{extra}" if extra else "")


def ts(title, targets, unit="short", w=8, stack=False, desc=""):
    return {
        "type": "timeseries", "title": title, "description": desc,
        "datasource": {"type": "prometheus", "uid": "${prom}"},
        "fieldConfig": {"defaults": {"unit": unit, "custom": {
            "lineWidth": 2, "fillOpacity": 10, "showPoints": "never",
            "stacking": {"mode": "normal" if stack else "none"}}}, "overrides": []},
        "options": {"legend": {"displayMode": "list", "placement": "bottom"}, "tooltip": {"mode": "multi"}},
        "targets": [{"refId": chr(65 + i), "expr": e, "legendFormat": l,
                     "datasource": {"type": "prometheus", "uid": "${prom}"}} for i, (e, l) in enumerate(targets)],
        "_w": w,
    }


def stat(title, expr, unit="short", w=6, desc=""):
    return {
        "type": "stat", "title": title, "description": desc,
        "datasource": {"type": "prometheus", "uid": "${prom}"},
        "fieldConfig": {"defaults": {"unit": unit}, "overrides": []},
        "options": {"reduceOptions": {"calcs": ["lastNotNull"]}, "graphMode": "area", "colorMode": "value"},
        "targets": [{"refId": "A", "expr": expr, "datasource": {"type": "prometheus", "uid": "${prom}"}}],
        "_w": w, "_h": 5,
    }


def row(title):
    return {"type": "row", "title": title, "collapsed": False, "panels": [], "_w": 24, "_h": 1}


def http_panels(svc):
    s = sel(svc, 'uri!~"/actuator.*"')
    return [
        row("HTTP"),
        ts("Request rate by URI", [(f'sum by (uri) (rate(http_server_requests_seconds_count{{{s}}}[{RATE}]))', "{{uri}}")], "reqps"),
        ts("Error rate (5xx)", [(f'sum by (uri) (rate(http_server_requests_seconds_count{{{s},outcome="SERVER_ERROR"}}[{RATE}]))', "{{uri}}")], "reqps"),
        ts("Avg latency by URI", [(f'sum by (uri) (rate(http_server_requests_seconds_sum{{{s}}}[{RATE}])) / sum by (uri) (rate(http_server_requests_seconds_count{{{s}}}[{RATE}]))', "{{uri}}")], "s"),
    ]


def jvm_panels(svc):
    s = sel(svc)
    return [
        row("JVM (container limit 512 MB)"),
        ts("Heap", [(f'sum(jvm_memory_used_bytes{{{s},area="heap"}})', "used"),
                    (f'sum(jvm_memory_committed_bytes{{{s},area="heap"}})', "committed"),
                    (f'sum(jvm_memory_max_bytes{{{s},area="heap"}})', "max")], "bytes", w=6),
        ts("Non-heap", [(f'sum by (id) (jvm_memory_used_bytes{{{s},area="nonheap"}})', "{{id}}")], "bytes", w=6, stack=True),
        ts("CPU", [(f'process_cpu_usage{{{s}}}', "process"), (f'system_cpu_usage{{{s}}}', "system")], "percentunit", w=6),
        ts("Threads & GC", [(f'jvm_threads_live_threads{{{s}}}', "live threads"),
                            (f'sum(rate(jvm_gc_pause_seconds_count{{{s}}}[{RATE}])) * 60', "GC pauses / min")], "short", w=6),
    ]


def kafka_publish(svc):
    s = sel(svc)
    return [
        ts("Events published / s", [(f'sum by (topic, outcome) (rate(events_published_seconds_count{{{s}}}[{RATE}]))', "{{topic}} {{outcome}}")], "ops"),
        ts("Publish latency (send -> ack)", [(f'sum by (topic) (rate(events_published_seconds_sum{{{s}}}[{RATE}])) / sum by (topic) (rate(events_published_seconds_count{{{s}}}[{RATE}]))', "{{topic}}")], "s"),
    ]


def kafka_consume(svc):
    s = sel(svc)
    return [
        ts("Events consumed / s", [(f'sum by (topic, outcome) (rate(events_consumed_seconds_count{{{s}}}[{RATE}]))', "{{topic}} {{outcome}}")], "ops"),
        ts("Consume processing time", [(f'sum by (topic) (rate(events_consumed_seconds_sum{{{s}}}[{RATE}])) / sum by (topic) (rate(events_consumed_seconds_count{{{s}}}[{RATE}]))', "{{topic}}")], "s"),
        ts("Produce -> consume lag", [(f'sum by (topic) (rate(events_consume_lag_seconds_sum{{{s}}}[{RATE}])) / sum by (topic) (rate(events_consume_lag_seconds_count{{{s}}}[{RATE}]))', "{{topic}}")], "s",
           desc="Includes time a sleeping free-tier instance needed to wake up."),
    ]


def business(svc):
    s = sel(svc)
    if svc == "api-gateway":
        return [row("Gateway routes"),
                ts("Requests by route", [(f'sum by (routeId) (rate(spring_cloud_gateway_requests_seconds_count{{{s}}}[{RATE}]))', "{{routeId}}")], "reqps", w=12),
                ts("Avg upstream latency by route", [(f'sum by (routeId) (rate(spring_cloud_gateway_requests_seconds_sum{{{s}}}[{RATE}])) / sum by (routeId) (rate(spring_cloud_gateway_requests_seconds_count{{{s}}}[{RATE}]))', "{{routeId}}")], "s", w=12)]
    if svc == "user-service":
        return [row("Business"), stat("Users registered (total)", f'sum(users_registered_total{{{s}}})', w=6),
                ts("Registrations / min", [(f'sum(rate(users_registered_total{{{s}}}[{RATE}])) * 60', "users")], w=18)]
    if svc == "order-service":
        return [row("Business & Kafka"), stat("Orders created (total)", f'sum(orders_created_total{{{s}}})', w=6),
                ts("Orders / min", [(f'sum(rate(orders_created_total{{{s}}}[{RATE}])) * 60', "orders")], w=18)] + kafka_publish(svc) + [
                stat("Publish failures (total)", f'sum(events_published_seconds_count{{{s},outcome="failure"}}) or vector(0)', w=8)]
    if svc == "payment-service":
        return [row("Business"),
                stat("Approved (total)", f'sum(payments_processed_total{{{s},status="APPROVED"}}) or vector(0)', w=6),
                stat("Declined (total)", f'sum(payments_processed_total{{{s},status="DECLINED"}}) or vector(0)', w=6),
                ts("Payments / min by status", [(f'sum by (status) (rate(payments_processed_total{{{s}}}[{RATE}])) * 60', "{{status}}")], w=12, stack=True),
                row("Kafka")] + kafka_consume(svc) + kafka_publish(svc)
    if svc == "notification-service":
        return [row("Business"),
                ts("Notifications stored / min by type", [(f'sum by (type) (rate(notifications_stored_total{{{s}}}[{RATE}])) * 60', "{{type}}")], w=24, stack=True),
                row("Kafka")] + kafka_consume(svc)
    return []


def logs_and_traces(svc):
    return [
        row("Logs & traces"),
        {"type": "logs", "title": f"Logs ({svc})", "datasource": {"type": "loki", "uid": "${loki}"},
         "options": {"showTime": True, "wrapLogMessage": True, "sortOrder": "Descending", "enableLogDetails": True},
         "targets": [{"refId": "A", "expr": f'{{service_name="{svc}"}}', "datasource": {"type": "loki", "uid": "${loki}"}}],
         "_w": 24, "_h": 10},
        {"type": "table", "title": f"Recent traces touching {svc}", "datasource": {"type": "tempo", "uid": "${tempo}"},
         "targets": [{"refId": "A", "queryType": "traceql", "limit": 20, "tableType": "traces",
                      "query": f'{{resource.service.name="{svc}"}}', "datasource": {"type": "tempo", "uid": "${tempo}"}}],
         "_w": 24, "_h": 9},
    ]


def layout(panels):
    x = y = rowh = 0
    for i, p in enumerate(panels, 1):
        w, h = p.pop("_w"), p.pop("_h", 8)
        if x + w > 24:
            x, y = 0, y + rowh
            rowh = 0
        p["gridPos"] = {"x": x, "y": y, "w": w, "h": h}
        p["id"] = i
        x += w
        rowh = max(rowh, h)
        if p["type"] == "row":
            x, y, rowh = 0, y + h, 0
    return panels


def dashboard(svc, desc):
    panels = layout(business(svc) + http_panels(svc) + jvm_panels(svc) + logs_and_traces(svc))
    ds = lambda name, typ, label: {"name": name, "label": label, "type": "datasource", "query": typ,
                                   "hide": 0, "refresh": 1, "current": {}}
    return {
        "uid": f"devops-setup-{svc}", "title": f"devops-setup / {svc}", "description": desc,
        "tags": ["devops-setup", svc], "timezone": "browser", "schemaVersion": 39, "version": 1,
        "refresh": "30s", "time": {"from": "now-1h", "to": "now"},
        "templating": {"list": [ds("prom", "prometheus", "Metrics"), ds("loki", "loki", "Logs"), ds("tempo", "tempo", "Traces")]},
        "links": [{"title": "Other services", "type": "dashboards", "tags": ["devops-setup"], "asDropdown": True}],
        "panels": panels,
    }


if __name__ == "__main__":
    for svc, desc in SERVICES.items():
        (OUT / f"{svc}.json").write_text(json.dumps(dashboard(svc, desc), indent=2) + "\n")
        print("wrote", OUT / f"{svc}.json")
