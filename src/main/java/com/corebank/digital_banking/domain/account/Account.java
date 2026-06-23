package com.corebank.digital_banking.domain.account;

import com.corebank.digital_banking.domain.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Account {
    private final UUID id;
    private final String holderName;
    private final BigDecimal balance;
    private final Instant createdAt;

    public Account(UUID id, String holderName, BigDecimal balance, Instant createdAt) {
        if (id == null) {
            throw new BusinessRuleException("Account ID cannot be null");
        }
        if (holderName == null || holderName.trim().isEmpty()) {
            throw new BusinessRuleException("Account holder name cannot be null or empty");
        }
        if (balance == null || balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Initial balance cannot be negative");
        }
        BigDecimal maxLimit = new BigDecimal("99999999999999.9999");
        if (balance.compareTo(maxLimit) > 0) {
            throw new BusinessRuleException("Initial balance exceeds maximum limit");
        }
        if (createdAt == null) {
            throw new BusinessRuleException("Created at timestamp cannot be null");
        }
        this.id = id;
        this.holderName = holderName;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getHolderName() {
        return holderName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
