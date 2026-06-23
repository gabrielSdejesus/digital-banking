package com.corebank.digital_banking.domain.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Transfer {
    private final UUID id;
    private final UUID sourceAccountId;
    private final UUID destinationAccountId;
    private final BigDecimal amount;
    private final String idempotencyKey;
    private final Instant createdAt;

    public Transfer(UUID id, UUID sourceAccountId, UUID destinationAccountId, BigDecimal amount, String idempotencyKey, Instant createdAt) {
        this.id = id;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
