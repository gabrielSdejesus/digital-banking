package com.corebank.digital_banking.infra.database.transaction;

import com.corebank.digital_banking.infra.database.account.AccountDbEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpringDataTransactionAccountRepository extends CrudRepository<AccountDbEntity, String> {

    @Query("SELECT * FROM account WHERE id = :id FOR UPDATE")
    Optional<AccountDbEntity> findByIdForUpdate(@Param("id") String id);
}
