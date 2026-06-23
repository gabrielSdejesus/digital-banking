package com.corebank.digital_banking.domain.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {
    Optional<AccountBalance> findBalanceByIdWithLock(UUID accountId);
    void saveBalance(AccountBalance accountBalance);
    Transfer saveTransfer(Transfer transfer);
    Optional<Transfer> findTransferById(UUID id);
    Optional<Transfer> findTransferByIdempotencyKey(String idempotencyKey);
    BigDecimal findTotalTransferredAmountToday(UUID accountId);
    void saveOutboxEvent(OutboxEvent outboxEvent);
    List<OutboxEvent> findPendingOutboxEvents();
    boolean claimOutboxEvent(Long id);
    void updateOutboxStatus(Long id, String status);
    void handleOutboxFailure(Long id);
    List<Transfer> findTransfersByAccountAndDateRangePaged(UUID accountId, Instant start, Instant end, Instant cursorCreatedAt, UUID cursorId, int limit);
    List<Transfer> findTransfersByAccountAndYear(UUID accountId, int year);

    boolean accountExists(UUID accountId);
}
