package com.corebank.digital_banking.infra.database.account;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataAccountRepository extends CrudRepository<AccountDbEntity, String> {
}
