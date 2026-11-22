package io.devopssetup.common.events;

import java.math.BigDecimal;
import java.time.Instant;

/** Event published on {@link Topics#ORDER_CREATED} (key = orderId). */
public record OrderCreated(
        String orderId,
        Long userId,
        String product,
        int quantity,
        BigDecimal price,
        Instant createdAt) {
}
