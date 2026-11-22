package io.devopssetup.common.events;

/** Kafka topic names shared by producers and consumers. */
public final class Topics {

    /** Published by order-service, consumed by payment-service and notification-service. */
    public static final String ORDER_CREATED = "orders.created";

    /** Published by payment-service, consumed by notification-service. */
    public static final String PAYMENT_PROCESSED = "payments.processed";

    private Topics() {
    }
}
