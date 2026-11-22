package io.devopssetup.common.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaSecurityEnvironmentPostProcessorTests {

    private final KafkaSecurityEnvironmentPostProcessor processor = new KafkaSecurityEnvironmentPostProcessor();

    @Test
    void addsNothingForLocalPlaintext() {
        MockEnvironment env = new MockEnvironment();
        processor.postProcessEnvironment(env, null);
        assertThat(env.getPropertySources().contains(KafkaSecurityEnvironmentPostProcessor.SOURCE_NAME)).isFalse();
    }

    @Test
    void configuresScramWithCaCert() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("KAFKA_USERNAME", "avnadmin")
                .withProperty("KAFKA_PASSWORD", "p\"w")
                .withProperty("KAFKA_CA_CERT", "-----BEGIN CERTIFICATE-----\\nabc\\n-----END CERTIFICATE-----");
        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.kafka.security.protocol")).isEqualTo("SASL_SSL");
        assertThat(env.getProperty("spring.kafka.properties.sasl.mechanism")).isEqualTo("SCRAM-SHA-256");
        assertThat(env.getProperty("spring.kafka.properties.sasl.jaas.config"))
                .isEqualTo("org.apache.kafka.common.security.scram.ScramLoginModule required username=\"avnadmin\" password=\"p\\\"w\";");
        assertThat(env.getProperty("spring.kafka.ssl.trust-store-type")).isEqualTo("PEM");
        assertThat(env.getProperty("spring.kafka.ssl.trust-store-certificates"))
                .isEqualTo("-----BEGIN CERTIFICATE-----\nabc\n-----END CERTIFICATE-----");
    }

    @Test
    void supportsPlainMechanism() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("KAFKA_USERNAME", "key")
                .withProperty("KAFKA_PASSWORD", "secret")
                .withProperty("KAFKA_SASL_MECHANISM", "PLAIN");
        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.kafka.properties.sasl.jaas.config"))
                .startsWith("org.apache.kafka.common.security.plain.PlainLoginModule required");
        assertThat(env.getProperty("spring.kafka.ssl.trust-store-type")).isNull();
    }
}
