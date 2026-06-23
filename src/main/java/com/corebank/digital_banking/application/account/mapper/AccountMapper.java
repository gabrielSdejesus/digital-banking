package com.corebank.digital_banking.application.account.mapper;

import com.corebank.digital_banking.application.account.dto.AccountResponse;
import com.corebank.digital_banking.application.account.dto.CreateAccountRequest;
import com.corebank.digital_banking.domain.account.Account;

import java.time.Instant;
import java.util.UUID;

public final class AccountMapper {

    private AccountMapper() {
    }

    public static Account toDomain(CreateAccountRequest request, UUID id, Instant createdAt) {
        return new Account(
                id,
                request.holderName(),
                request.initialBalance(),
                createdAt);
    }

    public static AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId().toString(),
                account.getHolderName(),
                account.getBalance(),
                account.getCreatedAt());
    }
}
