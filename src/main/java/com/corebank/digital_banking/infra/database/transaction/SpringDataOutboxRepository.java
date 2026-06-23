package com.corebank.digital_banking.infra.database.transaction;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpringDataOutboxRepository extends CrudRepository<OutboxEventDbEntity, Long> {
    List<OutboxEventDbEntity> findByStatus(String status);

    @Modifying
    @Query("UPDATE notification_outbox SET status = 'PROCESSING' WHERE id = :id AND status = 'PENDING'")
    int claimEvent(@Param("id") Long id);
}
