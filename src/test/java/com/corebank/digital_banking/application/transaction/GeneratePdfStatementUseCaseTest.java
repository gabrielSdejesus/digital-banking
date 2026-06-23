package com.corebank.digital_banking.application.transaction;

import com.corebank.digital_banking.application.exception.InvalidArgumentException;
import com.corebank.digital_banking.domain.account.AccountRepository;
import com.corebank.digital_banking.domain.exception.BusinessRuleException;
import com.corebank.digital_banking.domain.transaction.TransactionRepository;
import com.corebank.digital_banking.domain.transaction.Transfer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Generate PDF Statement Use Case Unit Tests")
class GeneratePdfStatementUseCaseTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    private Clock fixedClock;
    private GeneratePdfStatementUseCase generatePdfStatementUseCase;

    @BeforeEach
    void setUp() {
        // Set up fixed Clock for 2026-06-22 UTC
        fixedClock = Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneId.of("UTC"));
        generatePdfStatementUseCase = new GeneratePdfStatementUseCase(transactionRepository, accountRepository, fixedClock);
    }

    @Test
    @DisplayName("Should successfully generate statement PDF when parameters are valid")
    void shouldGeneratePdfSuccessfullyForValidYear() {
        UUID accountId = UUID.randomUUID();
        int year = 2025;

        when(transactionRepository.accountExists(accountId)).thenReturn(true);
        when(transactionRepository.findTransfersByAccountAndYear(accountId, year))
                .thenReturn(Collections.emptyList());

        byte[] pdfBytes = generatePdfStatementUseCase.execute(accountId.toString(), year);

        assertNotNull(pdfBytes);
    }

    @Test
    @DisplayName("Should throw InvalidArgumentException when requested year lies in the future")
    void shouldThrowInvalidArgumentExceptionForFutureYear() {
        UUID accountId = UUID.randomUUID();
        int futureYear = 2027; // Clock is fixed to 2026

        assertThrows(InvalidArgumentException.class, () -> {
            generatePdfStatementUseCase.execute(accountId.toString(), futureYear);
        });
    }

    @Test
    @DisplayName("Should throw InvalidArgumentException when requested year is prior to 1970")
    void shouldThrowInvalidArgumentExceptionForPre1970Year() {
        UUID accountId = UUID.randomUUID();
        int invalidYear = 1969;

        assertThrows(InvalidArgumentException.class, () -> {
            generatePdfStatementUseCase.execute(accountId.toString(), invalidYear);
        });
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when total statement transactions exceed capped limit of 5000")
    void shouldThrowBusinessRuleExceptionWhenRecordsExceedCappedLimit() {
        UUID accountId = UUID.randomUUID();
        int year = 2025;

        // Prepare a list exceeding the limit of 5,000 records
        List<Transfer> excessiveTransfers = new ArrayList<>();
        UUID destination = UUID.randomUUID();
        Instant now = Instant.now();
        BigDecimal amount = new BigDecimal("10.00");
        for (int i = 0; i < 5001; i++) {
            excessiveTransfers.add(new Transfer(UUID.randomUUID(), accountId, destination, amount, "idemp-" + i, now));
        }

        when(transactionRepository.accountExists(accountId)).thenReturn(true);
        when(transactionRepository.findTransfersByAccountAndYear(accountId, year))
                .thenReturn(excessiveTransfers);

        assertThrows(BusinessRuleException.class, () -> {
            generatePdfStatementUseCase.execute(accountId.toString(), year);
        });
    }
}
