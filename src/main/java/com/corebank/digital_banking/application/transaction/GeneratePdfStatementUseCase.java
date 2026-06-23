package com.corebank.digital_banking.application.transaction;

import com.corebank.digital_banking.application.exception.EntityNotFoundException;
import com.corebank.digital_banking.application.exception.InvalidArgumentException;
import com.corebank.digital_banking.domain.account.AccountRepository;
import com.corebank.digital_banking.domain.exception.BusinessRuleException;
import com.corebank.digital_banking.domain.transaction.TransactionRepository;
import com.corebank.digital_banking.domain.transaction.Transfer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GeneratePdfStatementUseCase {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    public GeneratePdfStatementUseCase(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public byte[] execute(String accountIdStr, int year) {
        if (accountIdStr == null || accountIdStr.trim().isEmpty()) {
            throw new InvalidArgumentException("Account ID is required", "accountId", "Account ID cannot be null or empty");
        }
        if (accountIdStr.trim().length() != 36) {
            throw new InvalidArgumentException("Invalid account ID size", "accountId", "Account ID must be exactly 36 characters");
        }
        UUID accountId;
        try {
            accountId = UUID.fromString(accountIdStr);
        } catch (IllegalArgumentException e) {
            throw new InvalidArgumentException("Invalid account ID format", "accountId", "Account ID must be a valid UUID");
        }

        int currentYear = LocalDate.now(clock).getYear();
        if (year < 1970 || year > currentYear) {
            throw new InvalidArgumentException("Invalid statement year", "year", "The year must be between 1970 and " + currentYear);
        }

        if (!transactionRepository.accountExists(accountId)) {
            throw new EntityNotFoundException("Account with ID " + accountIdStr + " not found");
        }

        List<Transfer> transfers = transactionRepository.findTransfersByAccountAndYear(accountId, year);

        if (transfers.size() > 5000) {
            throw new BusinessRuleException("Too many records for PDF export. Capped at 5,000");
        }

        // Fetch distinct account holder names to display in columns
        Set<UUID> accountIds = transfers.stream()
                .flatMap(t -> Stream.of(t.getSourceAccountId(), t.getDestinationAccountId()))
                .collect(Collectors.toSet());

        Map<UUID, String> accountNames = new HashMap<>();
        for (UUID id : accountIds) {
            accountRepository.findById(id).ifPresent(acc -> accountNames.put(id, acc.getHolderName()));
        }

        return PdfGenerator.generateStatementPdf(accountIdStr, year, transfers, accountNames);
    }
}
