package com.corebank.digital_banking.infra.database.account;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Table("account")
public class AccountDbEntity implements Persistable<String> {

    @Id
    @Column("id")
    private String id;

    @Column("holder_name")
    private String holderName;

    @Column("balance")
    private BigDecimal balance;

    @Column("created_at")
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    public AccountDbEntity() {}

    public AccountDbEntity(String id, String holderName, BigDecimal balance, Instant createdAt, boolean isNew) {
        this.id = id;
        this.holderName = holderName;
        this.balance = balance;
        this.createdAt = createdAt;
        this.isNew = isNew;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
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
