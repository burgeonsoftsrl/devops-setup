package io.devopssetup.notification;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("notifications")
public record Notification(
        @Id String id,
        Type type,
        @Indexed String orderId,
        @Indexed Long userId,
        String message,
        Instant receivedAt) {

    public enum Type { ORDER_CREATED, PAYMENT_PROCESSED }

    static Notification of(Type type, String orderId, Long userId, String message) {
        return new Notification(null, type, orderId, userId, message, Instant.now());
    }
}
