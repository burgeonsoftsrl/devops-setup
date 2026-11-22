package io.devopssetup.payment;

import io.devopssetup.common.events.OrderCreated;
import io.devopssetup.common.events.PaymentProcessed;
import io.devopssetup.common.events.PaymentProcessed.Status;
import io.devopssetup.common.events.Topics;
import io.devopssetup.common.kafka.EventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Consumes OrderCreated, "charges" the order, and publishes PaymentProcessed. */
@Component
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final PaymentRepository payments;
    private final EventPublisher publisher;
    private final MeterRegistry registry;
    private final BigDecimal approvalLimit;

    public PaymentProcessor(PaymentRepository payments, EventPublisher publisher, MeterRegistry registry,
                            @Value("${app.payments.approval-limit:1000}") BigDecimal approvalLimit) {
        this.payments = payments;
        this.publisher = publisher;
        this.registry = registry;
        this.approvalLimit = approvalLimit;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED)
    public void onOrderCreated(OrderCreated order) {
        Payment payment = payments.findByOrderId(order.orderId()).orElseGet(() -> {
            BigDecimal amount = order.price().multiply(BigDecimal.valueOf(order.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            // Toy rule so both outcomes can be demoed: large orders are declined.
            Status status = amount.compareTo(approvalLimit) <= 0 ? Status.APPROVED : Status.DECLINED;
            Payment saved = payments.save(new Payment(order.orderId(), order.userId(), amount, status));
            Counter.builder("payments.processed").description("Payments processed, by outcome")
                    .tag("status", status.name()).register(registry).increment();
            return saved;
        });
        log.info("Payment {} for order {}: {} {}", payment.getId(), order.orderId(), payment.getStatus(), payment.getAmount());

        // Publish even for an already-processed order: a redelivery may mean the previous publish was lost.
        publisher.publish(Topics.PAYMENT_PROCESSED, order.orderId(), new PaymentProcessed(payment.getId(), payment.getOrderId(),
                payment.getUserId(), payment.getAmount(), payment.getStatus(), payment.getProcessedAt()));
    }
}
