package io.devopssetup.notification;

import io.devopssetup.common.events.OrderCreated;
import io.devopssetup.common.events.PaymentProcessed;
import io.devopssetup.common.events.PaymentProcessed.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationListenerTests {

    NotificationRepository repo = mock(NotificationRepository.class);
    NotificationListener listener = new NotificationListener(repo);

    @Test
    void storesOrderCreated() {
        listener.onOrderCreated(new OrderCreated("o-1", 7L, "Book", 2, new BigDecimal("9.99"), Instant.now()));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().type()).isEqualTo(Notification.Type.ORDER_CREATED);
        assertThat(saved.getValue().message()).isEqualTo("Order received: 2 x Book");
        assertThat(saved.getValue().userId()).isEqualTo(7L);
    }

    @Test
    void storesPaymentProcessed() {
        listener.onPaymentProcessed(new PaymentProcessed(1L, "o-1", 7L, new BigDecimal("19.98"), Status.APPROVED, Instant.now()));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().type()).isEqualTo(Notification.Type.PAYMENT_PROCESSED);
        assertThat(saved.getValue().message()).isEqualTo("Payment APPROVED for 19.98");
    }
}
