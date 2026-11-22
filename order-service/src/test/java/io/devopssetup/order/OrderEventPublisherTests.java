package io.devopssetup.order;

import io.devopssetup.common.events.OrderCreated;
import io.devopssetup.common.events.Topics;
import io.devopssetup.common.kafka.EventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderEventPublisherTests {

    @Test
    void countsAndPublishesOrderCreated() {
        EventPublisher publisher = mock(EventPublisher.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new OrderEventPublisher(publisher, registry)
                .orderCreated(new Order("o-1", 1L, "Book", 1, new BigDecimal("9.99"), Instant.now()));

        verify(publisher).publish(eq(Topics.ORDER_CREATED), eq("o-1"), any(OrderCreated.class));
        assertThat(registry.get("orders.created").counter().count()).isEqualTo(1.0);
    }
}
