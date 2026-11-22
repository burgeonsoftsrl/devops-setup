package io.devopssetup.notification;

import io.devopssetup.common.events.OrderCreated;
import io.devopssetup.common.events.PaymentProcessed;
import io.devopssetup.common.events.Topics;
import io.devopssetup.notification.Notification.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** "Sends" notifications: logs each event and stores it in MongoDB. */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationRepository notifications;

    public NotificationListener(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED)
    public void onOrderCreated(OrderCreated e) {
        store(Notification.of(Type.ORDER_CREATED, e.orderId(), e.userId(),
                "Order received: %d x %s".formatted(e.quantity(), e.product())));
    }

    @KafkaListener(topics = Topics.PAYMENT_PROCESSED)
    public void onPaymentProcessed(PaymentProcessed e) {
        store(Notification.of(Type.PAYMENT_PROCESSED, e.orderId(), e.userId(),
                "Payment %s for %s".formatted(e.status(), e.amount())));
    }

    private void store(Notification n) {
        notifications.save(n);
        log.info("[notify user {}] {} (order {})", n.userId(), n.message(), n.orderId());
    }
}
