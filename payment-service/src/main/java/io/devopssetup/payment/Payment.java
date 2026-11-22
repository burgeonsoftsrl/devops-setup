package io.devopssetup.payment;

import io.devopssetup.common.events.PaymentProcessed.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Lives in its own Postgres schema (PAYMENTS_SCHEMA, default "payments"). */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique: makes event handling idempotent if OrderCreated is redelivered. */
    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private Instant processedAt = Instant.now();

    protected Payment() {
    }

    public Payment(String orderId, Long userId, BigDecimal amount, Status status) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public Long getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public Instant getProcessedAt() { return processedAt; }
}
