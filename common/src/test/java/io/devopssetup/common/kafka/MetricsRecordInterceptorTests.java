package io.devopssetup.common.kafka;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsRecordInterceptorTests {

    @Test
    void recordsConsumeTimeLagAndOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsRecordInterceptor interceptor = new MetricsRecordInterceptor(registry);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("orders.created", 0, 0L, "k", "v");

        interceptor.intercept(record, null);
        interceptor.success(record, null);
        interceptor.intercept(record, null);
        interceptor.failure(record, new RuntimeException("boom"), null);

        assertThat(registry.get("events.consumed").tag("topic", "orders.created").tag("outcome", "success").timer().count()).isEqualTo(1);
        assertThat(registry.get("events.consumed").tag("outcome", "failure").timer().count()).isEqualTo(1);
    }
}
