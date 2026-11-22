package io.devopssetup.common.observability;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityEnvironmentPostProcessorTests {

    private final ObservabilityEnvironmentPostProcessor processor = new ObservabilityEnvironmentPostProcessor();

    @Test
    void disablesOtlpExportWhenNothingConfigured() {
        MockEnvironment env = new MockEnvironment();
        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("management.otlp.tracing.export.enabled")).isEqualTo("false");
        assertThat(env.getProperty("management.otlp.logging.export.enabled")).isEqualTo("false");
        assertThat(env.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false");
        assertThat(env.getProperty("logging.structured.format.console")).isEqualTo("logstash");
        assertThat(env.getProperty("spring.kafka.listener.observation-enabled")).isEqualTo("true");
    }

    @Test
    void derivesAllSignalsFromGrafanaCloudStyleEnv() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "https://otlp.example.net/otlp/")
                .withProperty("OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf")
                .withProperty("OTEL_EXPORTER_OTLP_HEADERS", "Authorization=Basic%20abc%3D%3D")
                .withProperty("OTEL_SERVICE_NAME", "order-service")
                .withProperty("OTEL_RESOURCE_ATTRIBUTES", "deployment.environment.name=prod");
        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("management.otlp.tracing.endpoint")).isEqualTo("https://otlp.example.net/otlp/v1/traces");
        assertThat(env.getProperty("management.otlp.logging.endpoint")).isEqualTo("https://otlp.example.net/otlp/v1/logs");
        assertThat(env.getProperty("management.otlp.metrics.export.url")).isEqualTo("https://otlp.example.net/otlp/v1/metrics");
        assertThat(env.getProperty("management.otlp.tracing.transport")).isEqualTo("http");
        assertThat(env.getProperty("management.otlp.tracing.headers.Authorization")).isEqualTo("Basic abc==");
        assertThat(env.getProperty("management.otlp.metrics.export.headers.Authorization")).isEqualTo("Basic abc==");
        assertThat(env.getProperty("management.otlp.logging.headers.Authorization")).isEqualTo("Basic abc==");
        assertThat(env.getProperty("management.opentelemetry.resource-attributes.service.name")).isEqualTo("order-service");
        assertThat(env.getProperty("management.opentelemetry.resource-attributes.deployment.environment.name")).isEqualTo("prod");
    }

    @Test
    void perSignalEndpointsOverrideBase() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "http://tempo:4318/v1/traces")
                .withProperty("OTEL_EXPORTER_OTLP_LOGS_ENDPOINT", "http://loki:3100/otlp/v1/logs");
        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("management.otlp.tracing.endpoint")).isEqualTo("http://tempo:4318/v1/traces");
        assertThat(env.getProperty("management.otlp.logging.endpoint")).isEqualTo("http://loki:3100/otlp/v1/logs");
        assertThat(env.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false"); // Prometheus scrapes locally
    }

    @Test
    void explicitSettingsWin() {
        MockEnvironment env = new MockEnvironment().withProperty("management.tracing.sampling.probability", "0.2");
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("management.tracing.sampling.probability")).isEqualTo("0.2");
    }
}
