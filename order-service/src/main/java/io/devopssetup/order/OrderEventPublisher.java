package io.devopssetup.order;

import io.devopssetup.common.events.OrderCreated;
import io.devopssetup.common.events.Topics;
import io.devopssetup.common.kafka.EventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private final EventPublisher publisher;
    private final Counter ordersCreated;

    public OrderEventPublisher(EventPublisher publisher, MeterRegistry registry) {
        this.publisher = publisher;
        this.ordersCreated = Counter.builder("orders.placed").description("Orders placed (OrderCreated published)").register(registry);
    }

    public void orderCreated(Order order) {
        ordersCreated.increment();
        publisher.publish(Topics.ORDER_CREATED, order.id(), new OrderCreated(order.id(), order.userId(),
                order.product(), order.quantity(), order.price(), order.createdAt()));
    }
}
