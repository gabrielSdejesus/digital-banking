package com.corebank.digital_banking.infra.database.transaction;

import com.corebank.digital_banking.domain.transaction.AccountBalance;
import com.corebank.digital_banking.infra.database.account.AccountDbEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Transaction Repository Implementation Unit Tests")
class TransactionRepositoryImplTest {

    @Mock
    private SpringDataTransactionAccountRepository accountRepository;

    @Mock
    private SpringDataTransferRepository transferRepository;

    @Mock
    private SpringDataOutboxRepository outboxRepository;

    @InjectMocks
    private TransactionRepositoryImpl transactionRepositoryImpl;

    @Test
    @DisplayName("Should retrieve account balance using pessimistic lock successfully")
    void shouldFindBalanceByIdWithLockSuccessfully() {
        UUID accountId = UUID.randomUUID();
        AccountDbEntity dbEntity = new AccountDbEntity(accountId.toString(), "Gabriel Jesus", new BigDecimal("1000.00"), null, false);

        when(accountRepository.findByIdForUpdate(accountId.toString())).thenReturn(Optional.of(dbEntity));

        Optional<AccountBalance> result = transactionRepositoryImpl.findBalanceByIdWithLock(accountId);

        assertTrue(result.isPresent());
        assertEquals(accountId, result.get().getAccountId());
        assertEquals(new BigDecimal("1000.0000"), result.get().getBalance());
        verify(accountRepository, times(1)).findByIdForUpdate(accountId.toString());
    }

    @Test
    @DisplayName("Should successfully update account balance by setting isNew flag to false")
    void shouldSaveBalanceSuccessfullyMarkingAsNotNew() {
        UUID accountId = UUID.randomUUID();
        AccountBalance accountBalance = new AccountBalance(accountId, new BigDecimal("1200.00"));
        AccountDbEntity dbEntity = new AccountDbEntity(accountId.toString(), "Gabriel Jesus", new BigDecimal("1000.00"), null, true);

        when(accountRepository.findById(accountId.toString())).thenReturn(Optional.of(dbEntity));
        when(accountRepository.save(any(AccountDbEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        transactionRepositoryImpl.saveBalance(accountBalance);

        assertEquals(new BigDecimal("1200.0000"), dbEntity.getBalance());
        assertFalse(dbEntity.isNew()); // MUST be false to trigger SQL UPDATE instead of INSERT

        verify(accountRepository, times(1)).findById(accountId.toString());
        verify(accountRepository, times(1)).save(dbEntity);
    }

    @Test
    @DisplayName("Should retrieve and map pending outbox events successfully")
    void shouldFindPendingOutboxEventsSuccessfully() {
        OutboxEventDbEntity entity = new OutboxEventDbEntity(1L, "tx-123", "{\"amount\": 100}", "PENDING", 0, java.time.Instant.now());
        when(outboxRepository.findByStatus("PENDING")).thenReturn(java.util.List.of(entity));

        java.util.List<com.corebank.digital_banking.domain.transaction.OutboxEvent> results = transactionRepositoryImpl.findPendingOutboxEvents();

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).getId());
        assertEquals("tx-123", results.get(0).getTransferId());
        assertEquals("PENDING", results.get(0).getStatus());
        verify(outboxRepository, times(1)).findByStatus("PENDING");
    }

    @Test
    @DisplayName("Should update outbox status successfully when event is found")
    void shouldUpdateOutboxStatusSuccessfully() {
        OutboxEventDbEntity entity = new OutboxEventDbEntity(1L, "tx-123", "{\"amount\": 100}", "PENDING", 0, java.time.Instant.now());
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(outboxRepository.save(any(OutboxEventDbEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        transactionRepositoryImpl.updateOutboxStatus(1L, "PROCESSED");

        assertEquals("PROCESSED", entity.getStatus());
        verify(outboxRepository, times(1)).findById(1L);
        verify(outboxRepository, times(1)).save(entity);
    }

    @Test
    @DisplayName("Should retrieve and map transfer by ID successfully")
    void shouldFindTransferByIdSuccessfully() {
        UUID transferId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();
        TransferDbEntity dbEntity = new TransferDbEntity(transferId.toString(), sourceId.toString(), destId.toString(), new BigDecimal("100.00"), "idemp-key-123", java.time.Instant.now(), false);

        when(transferRepository.findById(transferId.toString())).thenReturn(Optional.of(dbEntity));

        Optional<com.corebank.digital_banking.domain.transaction.Transfer> result = transactionRepositoryImpl.findTransferById(transferId);

        assertTrue(result.isPresent());
        assertEquals(transferId, result.get().getId());
        assertEquals(sourceId, result.get().getSourceAccountId());
        assertEquals(destId, result.get().getDestinationAccountId());
        verify(transferRepository, times(1)).findById(transferId.toString());
    }
}
