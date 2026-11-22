package io.devopssetup.common.kafka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a few plain env vars into Kafka client security settings, so the same image works
 * against local PLAINTEXT Kafka and any managed SASL_SSL provider (Aiven, Confluent, Redpanda):
 *
 * <ul>
 *   <li>{@code KAFKA_USERNAME} + {@code KAFKA_PASSWORD} set &rarr; {@code SASL_SSL}</li>
 *   <li>{@code KAFKA_SASL_MECHANISM}: {@code SCRAM-SHA-256} (default), {@code SCRAM-SHA-512} or {@code PLAIN}</li>
 *   <li>{@code KAFKA_CA_CERT}: optional PEM of the provider's CA (Aiven needs it; public-CA providers don't)</li>
 * </ul>
 *
 * Nothing is added when {@code KAFKA_USERNAME} is empty (local dev). Added with lowest precedence,
 * so explicit {@code spring.kafka.*} settings still win.
 */
public class KafkaSecurityEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "kafkaSecurityFromEnv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        Map<String, Object> props = new LinkedHashMap<>();

        String username = env.getProperty("KAFKA_USERNAME");
        if (StringUtils.hasText(username)) {
            String mechanism = env.getProperty("KAFKA_SASL_MECHANISM", "SCRAM-SHA-256").trim();
            String loginModule = mechanism.equalsIgnoreCase("PLAIN")
                    ? "org.apache.kafka.common.security.plain.PlainLoginModule"
                    : "org.apache.kafka.common.security.scram.ScramLoginModule";
            props.put("spring.kafka.security.protocol", "SASL_SSL");
            props.put("spring.kafka.properties.sasl.mechanism", mechanism);
            props.put("spring.kafka.properties.sasl.jaas.config", "%s required username=\"%s\" password=\"%s\";"
                    .formatted(loginModule, jaasEscape(username.trim()), jaasEscape(env.getProperty("KAFKA_PASSWORD", ""))));
        }

        String caCert = env.getProperty("KAFKA_CA_CERT");
        if (StringUtils.hasText(caCert)) {
            props.put("spring.kafka.ssl.trust-store-type", "PEM");
            // Env vars often arrive with literal "\n" instead of newlines; normalise either form.
            props.put("spring.kafka.ssl.trust-store-certificates", caCert.replace("\\n", "\n").trim());
        }

        if (!props.isEmpty()) {
            env.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, props));
        }
    }

    private static String jaasEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
