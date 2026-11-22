package io.devopssetup.common.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

import java.time.Duration;

/**
 * Records, for every consumed record:
 * <ul>
 *   <li>{@code events.consumed}: timer (count + listener processing time), tags {@code topic}, {@code outcome}</li>
 *   <li>{@code events.consume.lag}: timer, time between the record's produce timestamp and processing start</li>
 * </ul>
 * Applied automatically by Spring Boot to the listener container factory.
 */
public class MetricsRecordInterceptor implements RecordInterceptor<Object, Object> {

    private final MeterRegistry registry;
    private final ThreadLocal<Long> start = new ThreadLocal<>();

    public MetricsRecordInterceptor(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        start.set(System.nanoTime());
        if (record.timestamp() > 0) {
            long lagMs = Math.max(0, System.currentTimeMillis() - record.timestamp());
            Timer.builder("events.consume.lag")
                    .description("Time from produce to consume")
                    .tag("topic", record.topic())
                    .register(registry)
                    .record(Duration.ofMillis(lagMs));
        }
        return record;
    }

    @Override
    public void success(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        record(record, "success");
    }

    @Override
    public void failure(ConsumerRecord<Object, Object> record, Exception exception, Consumer<Object, Object> consumer) {
        record(record, "failure");
    }

    private void record(ConsumerRecord<Object, Object> record, String outcome) {
        Long t0 = start.get();
        start.remove();
        if (t0 == null) {
            return;
        }
        Timer.builder("events.consumed")
                .description("Kafka events consumed (listener processing time)")
                .tag("topic", record.topic()).tag("outcome", outcome)
                .register(registry)
                .record(Duration.ofNanos(System.nanoTime() - t0));
    }
}
