package com.corebank.digital_banking.infra.database.transaction;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("notification_outbox")
public class OutboxEventDbEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("transfer_id")
    private String transferId;

    @Column("payload")
    private String payload;

    @Column("status")
    private String status;

    @Column("retry_count")
    private int retryCount;

    @Column("created_at")
    private Instant createdAt;

    public OutboxEventDbEntity() {}

    public OutboxEventDbEntity(Long id, String transferId, String payload, String status, int retryCount, Instant createdAt) {
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

    public void setId(Long id) {
        this.id = id;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
