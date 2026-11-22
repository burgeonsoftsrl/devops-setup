package io.devopssetup.order;

import io.devopssetup.common.events.OrderCreated;
import io.devopssetup.common.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, Object> kafka;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    public void orderCreated(Order order) {
        var event = new OrderCreated(order.id(), order.userId(), order.product(), order.quantity(),
                order.price(), order.createdAt());
        kafka.send(Topics.ORDER_CREATED, order.id(), event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish OrderCreated for order {}", order.id(), ex);
            } else {
                log.info("Published OrderCreated order={} partition={} offset={}", order.id(),
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
