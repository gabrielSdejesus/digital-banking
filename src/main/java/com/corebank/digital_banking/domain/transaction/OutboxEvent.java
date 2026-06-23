package com.corebank.digital_banking.domain.transaction;

import java.time.Instant;

public class OutboxEvent {
    private final Long id;
    private final String transferId;
    private final String payload;
    private final String status;
    private final int retryCount;
    private final Instant createdAt;

    public OutboxEvent(Long id, String transferId, String payload, String status, int retryCount, Instant createdAt) {
        this.id = id;
        this.transferId = transferId;
        this.payload = payload;
        this.status = status;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getTransferId() {
        return transferId;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
