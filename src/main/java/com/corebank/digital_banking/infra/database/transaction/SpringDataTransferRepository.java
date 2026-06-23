package com.corebank.digital_banking.infra.database.transaction;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SpringDataTransferRepository extends CrudRepository<TransferDbEntity, String> {

    @Query("SELECT * FROM transfer WHERE (source_account_id = :accountId OR destination_account_id = :accountId) " +
           "AND created_at >= :start AND created_at <= :end " +
           "AND (:cursorCreatedAt IS NULL OR created_at < :cursorCreatedAt OR (created_at = :cursorCreatedAt AND id < :cursorId)) " +
           "ORDER BY created_at DESC, id DESC LIMIT :limit")
    List<TransferDbEntity> findTransfersByAccountAndDateRangePaged(
            @Param("accountId") String accountId,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") String cursorId,
            @Param("limit") int limit
    );

    @Query("SELECT * FROM transfer WHERE (source_account_id = :accountId OR destination_account_id = :accountId) AND YEAR(created_at) = :year ORDER BY created_at DESC")
    List<TransferDbEntity> findTransfersByAccountAndYear(
            @Param("accountId") String accountId,
            @Param("year") int year
    );



    @Query("SELECT * FROM transfer WHERE idempotency_key = :idempotencyKey")
    Optional<TransferDbEntity> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transfer WHERE source_account_id = :accountId AND created_at >= :startOfToday")
    BigDecimal sumAmountTransferredSince(
            @Param("accountId") String accountId,
            @Param("startOfToday") Instant startOfToday
    );
}
