package com.corebank.digital_banking.infra.database.transaction;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Table("transfer")
public class TransferDbEntity implements Persistable<String> {

    @Id
    @Column("id")
    private String id;

    @Column("source_account_id")
    private String sourceAccountId;

    @Column("destination_account_id")
    private String destinationAccountId;

    @Column("amount")
    private BigDecimal amount;

    @Column("idempotency_key")
    private String idempotencyKey;

    @Column("created_at")
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    public TransferDbEntity() {}

    public TransferDbEntity(String id, String sourceAccountId, String destinationAccountId, BigDecimal amount, String idempotencyKey, Instant createdAt, boolean isNew) {
        this.id = id;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.isNew = isNew;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(String sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public String getDestinationAccountId() {
        return destinationAccountId;
    }

    public void setDestinationAccountId(String destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }
}
