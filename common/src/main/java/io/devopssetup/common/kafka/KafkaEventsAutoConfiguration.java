package io.devopssetup.common.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/** Instrumented publisher + consumer metrics for services that have Kafka on the classpath. */
@AutoConfiguration(after = {KafkaAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class})
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaEventsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({KafkaTemplate.class, MeterRegistry.class})
    EventPublisher eventPublisher(KafkaTemplate<String, Object> kafkaTemplate, MeterRegistry registry) {
        return new EventPublisher(kafkaTemplate, registry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)
    MetricsRecordInterceptor metricsRecordInterceptor(MeterRegistry registry) {
        return new MetricsRecordInterceptor(registry);
    }
}
