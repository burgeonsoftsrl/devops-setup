package io.devopssetup.common.kafka;

import io.devopssetup.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Declares the shared topics so Spring's KafkaAdmin creates them on startup if missing
 * (2 partitions, broker-default replication). Every service declares them; creation is idempotent.
 * Disable with {@code app.kafka.create-topics=false} (e.g. when topics are managed by the provider).
 */
@AutoConfiguration
@ConditionalOnClass(KafkaAdmin.class)
@ConditionalOnProperty(name = "app.kafka.create-topics", havingValue = "true", matchIfMissing = true)
public class KafkaTopicsAutoConfiguration {

    @Bean
    NewTopic orderCreatedTopic() {
        return TopicBuilder.name(Topics.ORDER_CREATED).partitions(2).build();
    }

    @Bean
    NewTopic paymentProcessedTopic() {
        return TopicBuilder.name(Topics.PAYMENT_PROCESSED).partitions(2).build();
    }
}
