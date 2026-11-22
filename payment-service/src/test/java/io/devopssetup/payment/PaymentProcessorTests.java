package io.devopssetup.payment;

import io.devopssetup.common.events.OrderCreated;
import io.devopssetup.common.events.PaymentProcessed;
import io.devopssetup.common.events.PaymentProcessed.Status;
import io.devopssetup.common.events.Topics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import io.devopssetup.common.kafka.EventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentProcessorTests {

    PaymentRepository payments = mock(PaymentRepository.class);
    EventPublisher kafka = mock(EventPublisher.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PaymentProcessor processor = new PaymentProcessor(payments, kafka, registry, new BigDecimal("1000"));

    @BeforeEach
    void setUp() {
        when(payments.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void approvesSmallOrderAndPublishes() {
        when(payments.findByOrderId("o-1")).thenReturn(Optional.empty());

        processor.onOrderCreated(new OrderCreated("o-1", 7L, "Book", 3, new BigDecimal("9.99"), Instant.now()));

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafka).publish(eq(Topics.PAYMENT_PROCESSED), eq("o-1"), event.capture());
        PaymentProcessed processed = (PaymentProcessed) event.getValue();
        assertThat(processed.status()).isEqualTo(Status.APPROVED);
        assertThat(processed.amount()).isEqualByComparingTo("29.97");
        assertThat(processed.userId()).isEqualTo(7L);
        assertThat(registry.get("payments.processed").tag("status", "APPROVED").counter().count()).isEqualTo(1.0);
    }

    @Test
    void declinesLargeOrder() {
        when(payments.findByOrderId("o-2")).thenReturn(Optional.empty());

        processor.onOrderCreated(new OrderCreated("o-2", 7L, "Car", 1, new BigDecimal("25000"), Instant.now()));

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafka).publish(eq(Topics.PAYMENT_PROCESSED), eq("o-2"), event.capture());
        assertThat(((PaymentProcessed) event.getValue()).status()).isEqualTo(Status.DECLINED);
    }

    @Test
    void redeliveryDoesNotChargeTwice() {
        Payment existing = new Payment("o-3", 7L, new BigDecimal("5.00"), Status.APPROVED);
        when(payments.findByOrderId("o-3")).thenReturn(Optional.of(existing));

        processor.onOrderCreated(new OrderCreated("o-3", 7L, "Pen", 1, new BigDecimal("5.00"), Instant.now()));

        verify(payments, never()).save(any());
        verify(kafka).publish(eq(Topics.PAYMENT_PROCESSED), eq("o-3"), any());
    }
}
