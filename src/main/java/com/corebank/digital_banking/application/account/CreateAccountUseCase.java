package com.corebank.digital_banking.application.account;

import com.corebank.digital_banking.application.account.dto.AccountResponse;
import com.corebank.digital_banking.application.account.dto.CreateAccountRequest;
import com.corebank.digital_banking.application.account.mapper.AccountMapper;
import com.corebank.digital_banking.application.exception.EntityNotFoundException;
import com.corebank.digital_banking.domain.account.Account;
import com.corebank.digital_banking.domain.account.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CreateAccountUseCase {

    private final AccountRepository accountRepository;

    public CreateAccountUseCase(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AccountResponse execute(CreateAccountRequest request) {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();
        Account domainAccount = AccountMapper.toDomain(request, accountId, now);

        accountRepository.save(domainAccount);

        Account fetchedAccount = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account with ID " + accountId + " not found"));

        return AccountMapper.toResponse(fetchedAccount);
    }
}
