package io.devopssetup.common.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps the standard OpenTelemetry env vars onto Spring Boot's OTLP settings, so every service is
 * configured the same way (Grafana Cloud in prod, Tempo/Loki locally):
 *
 * <ul>
 *   <li>{@code OTEL_EXPORTER_OTLP_ENDPOINT}: base URL; {@code /v1/traces}, {@code /v1/logs} and
 *       {@code /v1/metrics} are appended per signal</li>
 *   <li>{@code OTEL_EXPORTER_OTLP_{TRACES,LOGS,METRICS}_ENDPOINT}: full per-signal URL (overrides the base)</li>
 *   <li>{@code OTEL_EXPORTER_OTLP_HEADERS}: {@code k=v,k2=v2} (values URL-decoded), sent with every signal</li>
 *   <li>{@code OTEL_EXPORTER_OTLP_PROTOCOL}: {@code http/protobuf} (default) or {@code grpc} (traces/logs only)</li>
 *   <li>{@code OTEL_SERVICE_NAME}, {@code OTEL_RESOURCE_ATTRIBUTES}: resource attributes</li>
 * </ul>
 *
 * A signal whose endpoint is not configured is not exported (no connection-refused noise locally or
 * in tests). Prometheus scraping via {@code /actuator/prometheus} is always available.
 * Everything is added with lowest precedence, so explicit {@code management.*} settings still win.
 */
public class ObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "observabilityFromEnv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        Map<String, Object> p = new LinkedHashMap<>();

        // --- defaults shared by all services ---
        p.put("management.tracing.sampling.probability", "1.0");         // low traffic: keep every trace
        p.put("management.otlp.metrics.export.base-time-unit", "seconds"); // same metric names as Prometheus
        p.put("management.otlp.metrics.export.step", "60s");
        p.put("logging.structured.format.console", "logstash");           // JSON logs incl. traceId/spanId (MDC)
        p.put("spring.kafka.template.observation-enabled", "true");       // trace context through Kafka headers
        p.put("spring.kafka.listener.observation-enabled", "true");
        p.put("spring.reactor.context-propagation", "auto");              // WebFlux (gateway): MDC + tracing

        // --- OTLP exporters from OTEL_* env vars ---
        String base = trimSlash(env.getProperty("OTEL_EXPORTER_OTLP_ENDPOINT"));
        String traces = signalEndpoint(env, base, "TRACES", "/v1/traces");
        String logs = signalEndpoint(env, base, "LOGS", "/v1/logs");
        String metrics = signalEndpoint(env, base, "METRICS", "/v1/metrics");
        Map<String, String> headers = parseKeyValues(env.getProperty("OTEL_EXPORTER_OTLP_HEADERS"), true);
        String transport = "grpc".equalsIgnoreCase(env.getProperty("OTEL_EXPORTER_OTLP_PROTOCOL", "").trim()) ? "grpc" : "http";

        configureSignal(p, "management.otlp.tracing", "management.otlp.tracing.export.enabled", traces, headers, transport);
        configureSignal(p, "management.otlp.logging", "management.otlp.logging.export.enabled", logs, headers, transport);
        if (metrics != null) {
            p.put("management.otlp.metrics.export.enabled", "true");
            p.put("management.otlp.metrics.export.url", metrics);
            headers.forEach((k, v) -> p.put("management.otlp.metrics.export.headers." + k, v));
        } else {
            p.put("management.otlp.metrics.export.enabled", "false");
        }

        // --- resource attributes ---
        parseKeyValues(env.getProperty("OTEL_RESOURCE_ATTRIBUTES"), true)
                .forEach((k, v) -> p.put("management.opentelemetry.resource-attributes." + k, v));
        String serviceName = env.getProperty("OTEL_SERVICE_NAME");
        if (StringUtils.hasText(serviceName)) {
            p.put("management.opentelemetry.resource-attributes.service.name", serviceName.trim());
        }

        env.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, p));
    }

    private static void configureSignal(Map<String, Object> p, String prefix, String enabledKey, String endpoint,
                                        Map<String, String> headers, String transport) {
        if (endpoint == null) {
            p.put(enabledKey, "false");
            return;
        }
        p.put(enabledKey, "true");
        p.put(prefix + ".endpoint", endpoint);
        p.put(prefix + ".transport", transport);
        headers.forEach((k, v) -> p.put(prefix + ".headers." + k, v));
    }

    private static String signalEndpoint(Environment env, String base, String signal, String path) {
        String explicit = trimSlash(env.getProperty("OTEL_EXPORTER_OTLP_" + signal + "_ENDPOINT"));
        if (explicit != null) {
            return explicit;
        }
        return base == null ? null : base + path;
    }

    /** Parses the OTel {@code key1=value1,key2=value2} format. */
    static Map<String, String> parseKeyValues(String raw, boolean urlDecode) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!StringUtils.hasText(raw)) {
            return out;
        }
        for (String pair : raw.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            out.put(key, urlDecode ? URLDecoder.decode(value, StandardCharsets.UTF_8) : value);
        }
        return out;
    }

    private static String trimSlash(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        s = s.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
