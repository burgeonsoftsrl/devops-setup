package io.devopssetup.common.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes events and records {@code events.published} (timer: count + send-to-ack latency),
 * tagged with {@code topic} and {@code outcome}. Trace context travels in the record headers
 * (KafkaTemplate observation is enabled).
 */
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, Object> kafka;
    private final MeterRegistry registry;

    public EventPublisher(KafkaTemplate<String, Object> kafka, MeterRegistry registry) {
        this.kafka = kafka;
        this.registry = registry;
    }

    public CompletableFuture<SendResult<String, Object>> publish(String topic, String key, Object event) {
        Timer.Sample sample = Timer.start(registry);
        String type = event.getClass().getSimpleName();
        CompletableFuture<SendResult<String, Object>> future;
        try {
            future = kafka.send(topic, key, event);
        } catch (RuntimeException e) { // e.g. metadata timeout while the broker is unreachable
            stop(sample, topic, "failure");
            log.error("Failed to publish {} key={} to {}", type, key, topic, e);
            return CompletableFuture.failedFuture(e);
        }
        return future.whenComplete((result, ex) -> {
            if (ex != null) {
                stop(sample, topic, "failure");
                log.error("Failed to publish {} key={} to {}", type, key, topic, ex);
            } else {
                stop(sample, topic, "success");
                log.info("Published {} key={} topic={} partition={} offset={}", type, key, topic,
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }

    private void stop(Timer.Sample sample, String topic, String outcome) {
        sample.stop(Timer.builder("events.published")
                .description("Kafka events published (send to broker ack)")
                .tag("topic", topic).tag("outcome", outcome)
                .register(registry));
    }
}
