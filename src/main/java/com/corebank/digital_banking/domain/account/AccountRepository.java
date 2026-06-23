package com.corebank.digital_banking.domain.account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
    Account save(Account account);
    Optional<Account> findById(UUID id);
}
