package com.corebank.digital_banking.domain.transaction;

import com.corebank.digital_banking.domain.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public class AccountBalance {
    private final UUID accountId;
    private BigDecimal balance;

    public AccountBalance(UUID accountId, BigDecimal balance) {
        if (accountId == null) {
            throw new BusinessRuleException("Account ID cannot be null");
        }
        if (balance == null || balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Balance cannot be negative");
        }
        this.accountId = accountId;
        this.balance = balance.setScale(4, RoundingMode.HALF_DOWN);
    }

    public UUID getAccountId() {
        return accountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void debit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Debit amount must be positive");
        }
        if (this.balance.compareTo(amount) < 0) {
            throw new BusinessRuleException("Insufficient funds for this transfer");
        }
        this.balance = this.balance.subtract(amount).setScale(4, RoundingMode.HALF_DOWN);
    }

    public void credit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Credit amount must be positive");
        }
        this.balance = this.balance.add(amount).setScale(4, RoundingMode.HALF_DOWN);
    }
}
