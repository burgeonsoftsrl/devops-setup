package io.devopssetup.payment;

import io.devopssetup.common.events.OrderCreated;
import io.devopssetup.common.events.PaymentProcessed;
import io.devopssetup.common.events.PaymentProcessed.Status;
import io.devopssetup.common.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Consumes OrderCreated, "charges" the order, and publishes PaymentProcessed. */
@Component
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final PaymentRepository payments;
    private final KafkaTemplate<String, Object> kafka;
    private final BigDecimal approvalLimit;

    public PaymentProcessor(PaymentRepository payments, KafkaTemplate<String, Object> kafka,
                            @Value("${app.payments.approval-limit:1000}") BigDecimal approvalLimit) {
        this.payments = payments;
        this.kafka = kafka;
        this.approvalLimit = approvalLimit;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED)
    public void onOrderCreated(OrderCreated order) {
        Payment payment = payments.findByOrderId(order.orderId()).orElseGet(() -> {
            BigDecimal amount = order.price().multiply(BigDecimal.valueOf(order.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            // Toy rule so both outcomes can be demoed: large orders are declined.
            Status status = amount.compareTo(approvalLimit) <= 0 ? Status.APPROVED : Status.DECLINED;
            return payments.save(new Payment(order.orderId(), order.userId(), amount, status));
        });
        log.info("Payment {} for order {}: {} {}", payment.getId(), order.orderId(), payment.getStatus(), payment.getAmount());

        // Publish even for an already-processed order: a redelivery may mean the previous publish was lost.
        kafka.send(Topics.PAYMENT_PROCESSED, order.orderId(), new PaymentProcessed(payment.getId(), payment.getOrderId(),
                payment.getUserId(), payment.getAmount(), payment.getStatus(), payment.getProcessedAt()));
    }
}
