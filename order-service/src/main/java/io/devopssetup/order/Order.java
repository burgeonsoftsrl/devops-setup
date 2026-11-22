package io.devopssetup.order;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.math.BigDecimal;
import java.time.Instant;

@RedisHash("orders")
public record Order(
        @Id String id,
        @Indexed Long userId,
        String product,
        int quantity,
        BigDecimal price,
        Instant createdAt) {
}
