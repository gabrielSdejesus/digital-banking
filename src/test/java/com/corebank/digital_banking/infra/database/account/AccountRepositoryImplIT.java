package com.corebank.digital_banking.infra.database.account;

import com.corebank.digital_banking.domain.account.Account;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@DisplayName("Account Repository Integration Tests")
class AccountRepositoryImplIT {

    @Autowired
    private AccountRepositoryImpl accountRepositoryImpl;

    @Autowired
    private SpringDataAccountRepository springDataAccountRepository;

    @Test
    @DisplayName("Should successfully save and retrieve account from H2 database")
    void shouldSaveAndRetrieveAccountFromDatabase() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account(accountId, "Gabriel Jesus Integration", new BigDecimal("1500.0000"), java.time.Instant.now());

        Account savedAccount = accountRepositoryImpl.save(account);

        assertNotNull(savedAccount);
        assertEquals(accountId, savedAccount.getId());
        assertEquals("Gabriel Jesus Integration", savedAccount.getHolderName());
        assertEquals(new BigDecimal("1500.0000"), savedAccount.getBalance());

        Optional<AccountDbEntity> dbEntityOpt = springDataAccountRepository.findById(accountId.toString());
        assertTrue(dbEntityOpt.isPresent());
        AccountDbEntity dbEntity = dbEntityOpt.get();
        assertEquals(accountId.toString(), dbEntity.getId());
        assertEquals("Gabriel Jesus Integration", dbEntity.getHolderName());
        assertEquals(0, new BigDecimal("1500.0000").compareTo(dbEntity.getBalance()));
    }
}
