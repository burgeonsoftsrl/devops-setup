package io.devopssetup.common.events;

import java.math.BigDecimal;
import java.time.Instant;

/** Event published on {@link Topics#PAYMENT_PROCESSED} (key = orderId). */
public record PaymentProcessed(
        Long paymentId,
        String orderId,
        Long userId,
        BigDecimal amount,
        Status status,
        Instant processedAt) {

    public enum Status { APPROVED, DECLINED }
}
