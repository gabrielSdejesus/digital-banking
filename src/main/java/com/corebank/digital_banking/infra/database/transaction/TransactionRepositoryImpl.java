package com.corebank.digital_banking.infra.database.transaction;

import com.corebank.digital_banking.application.exception.EntityNotFoundException;
import com.corebank.digital_banking.domain.transaction.AccountBalance;
import com.corebank.digital_banking.domain.transaction.OutboxEvent;
import com.corebank.digital_banking.domain.transaction.TransactionRepository;
import com.corebank.digital_banking.domain.transaction.Transfer;
import com.corebank.digital_banking.infra.database.account.AccountDbEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TransactionRepositoryImpl implements TransactionRepository {

    private final SpringDataTransactionAccountRepository accountRepository;
    private final SpringDataTransferRepository transferRepository;
    private final SpringDataOutboxRepository outboxRepository;

    public TransactionRepositoryImpl(
            SpringDataTransactionAccountRepository accountRepository,
            SpringDataTransferRepository transferRepository,
            SpringDataOutboxRepository outboxRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
    }

    @Override
    public Optional<AccountBalance> findBalanceByIdWithLock(UUID accountId) {
        return accountRepository.findByIdForUpdate(accountId.toString())
                .map(entity -> new AccountBalance(
                        UUID.fromString(entity.getId()),
                        entity.getBalance()
                ));
    }

    @Override
    public void saveBalance(AccountBalance accountBalance) {
        String accountIdStr = accountBalance.getAccountId().toString();
        AccountDbEntity dbEntity = accountRepository.findById(accountIdStr)
                .orElseThrow(() -> new EntityNotFoundException("Account with ID " + accountIdStr + " not found"));

        dbEntity.setBalance(accountBalance.getBalance());
        dbEntity.setNew(false);

        accountRepository.save(dbEntity);
    }

    @Override
    public Transfer saveTransfer(Transfer transfer) {
        TransferDbEntity dbEntity = new TransferDbEntity(
                transfer.getId().toString(),
                transfer.getSourceAccountId().toString(),
                transfer.getDestinationAccountId().toString(),
                transfer.getAmount(),
                transfer.getIdempotencyKey(),
                transfer.getCreatedAt(),
                true
        );

        TransferDbEntity saved = transferRepository.save(dbEntity);

        return new Transfer(
                UUID.fromString(saved.getId()),
                UUID.fromString(saved.getSourceAccountId()),
                UUID.fromString(saved.getDestinationAccountId()),
                saved.getAmount(),
                saved.getIdempotencyKey(),
                saved.getCreatedAt()
        );
    }

    @Override
    public Optional<Transfer> findTransferById(UUID id) {
        return transferRepository.findById(id.toString())
                .map(entity -> new Transfer(
                        UUID.fromString(entity.getId()),
                        UUID.fromString(entity.getSourceAccountId()),
                        UUID.fromString(entity.getDestinationAccountId()),
                        entity.getAmount(),
                        entity.getIdempotencyKey(),
                        entity.getCreatedAt()
                ));
    }

    @Override
    public Optional<Transfer> findTransferByIdempotencyKey(String idempotencyKey) {
        return transferRepository.findByIdempotencyKey(idempotencyKey)
                .map(entity -> new Transfer(
                        UUID.fromString(entity.getId()),
                        UUID.fromString(entity.getSourceAccountId()),
                        UUID.fromString(entity.getDestinationAccountId()),
                        entity.getAmount(),
                        entity.getIdempotencyKey(),
                        entity.getCreatedAt()
                ));
    }

    @Override
    public java.math.BigDecimal findTotalTransferredAmountToday(UUID accountId) {
        Instant startOfToday = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        return transferRepository.sumAmountTransferredSince(accountId.toString(), startOfToday);
    }

    @Override
    public void saveOutboxEvent(OutboxEvent outboxEvent) {
        OutboxEventDbEntity dbEntity = new OutboxEventDbEntity(
            null,
            outboxEvent.getTransferId(),
            outboxEvent.getPayload(),
            outboxEvent.getStatus(),
            outboxEvent.getRetryCount(),
            outboxEvent.getCreatedAt()
        );

        outboxRepository.save(dbEntity);
    }

    @Override
    public List<OutboxEvent> findPendingOutboxEvents() {
        List<OutboxEventDbEntity> dbEntities = outboxRepository.findByStatus("PENDING");
        return dbEntities.stream()
                .map(entity -> new OutboxEvent(
                        entity.getId(),
                        entity.getTransferId(),
                        entity.getPayload(),
                        entity.getStatus(),
                        entity.getRetryCount(),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public boolean claimOutboxEvent(Long id) {
        return outboxRepository.claimEvent(id) == 1;
    }

    @Override
    public void updateOutboxStatus(Long id, String status) {
        outboxRepository.findById(id).ifPresent(entity -> {
            entity.setStatus(status);
            outboxRepository.save(entity);
        });
    }

    @Override
    public void handleOutboxFailure(Long id) {
        outboxRepository.findById(id).ifPresent(entity -> {
            int currentRetries = entity.getRetryCount() + 1;
            entity.setRetryCount(currentRetries);
            if (currentRetries >= 3) {
                entity.setStatus("DLQ");
            } else {
                entity.setStatus("PENDING");
            }
            outboxRepository.save(entity);
        });
    }

    @Override
    public List<Transfer> findTransfersByAccountAndDateRangePaged(
            UUID accountId, Instant start, Instant end, Instant cursorCreatedAt, UUID cursorId, int limit) {
        List<TransferDbEntity> dbEntities = transferRepository.findTransfersByAccountAndDateRangePaged(
                accountId.toString(),
                start,
                end,
                cursorCreatedAt,
                cursorId != null ? cursorId.toString() : null,
                limit
        );

        return dbEntities.stream()
                .map(entity -> new Transfer(
                        UUID.fromString(entity.getId()),
                        UUID.fromString(entity.getSourceAccountId()),
                        UUID.fromString(entity.getDestinationAccountId()),
                        entity.getAmount(),
                        entity.getIdempotencyKey(),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public List<Transfer> findTransfersByAccountAndYear(UUID accountId, int year) {
        List<TransferDbEntity> dbEntities = transferRepository.findTransfersByAccountAndYear(
                accountId.toString(),
                year
        );

        return dbEntities.stream()
                .map(entity -> new Transfer(
                        UUID.fromString(entity.getId()),
                        UUID.fromString(entity.getSourceAccountId()),
                        UUID.fromString(entity.getDestinationAccountId()),
                        entity.getAmount(),
                        entity.getIdempotencyKey(),
                        entity.getCreatedAt()
                ))
                .toList();
    }



    @Override
    public boolean accountExists(UUID accountId) {
        return accountRepository.existsById(accountId.toString());
    }
}
