package com.corebank.digital_banking.application.account.mapper;

import com.corebank.digital_banking.application.account.dto.AccountResponse;
import com.corebank.digital_banking.application.account.dto.CreateAccountRequest;
import com.corebank.digital_banking.domain.account.Account;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Account Mapper Unit Tests")
class AccountMapperTest {

    @Test
    @DisplayName("Should map CreateAccountRequest DTO to domain Account entity")
    void shouldMapCreateAccountRequestToDomain() {
        CreateAccountRequest request = new CreateAccountRequest("Gabriel Jesus", new BigDecimal("1500.00"));
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Account account = AccountMapper.toDomain(request, id, now);

        assertNotNull(account);
        assertEquals(id, account.getId());
        assertEquals("Gabriel Jesus", account.getHolderName());
        assertEquals(new BigDecimal("1500.00"), account.getBalance());
        assertEquals(now, account.getCreatedAt());
    }

    @Test
    @DisplayName("Should map domain Account entity to AccountResponse DTO")
    void shouldMapDomainToAccountResponse() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Account account = new Account(id, "Gabriel Jesus", new BigDecimal("1500.00"), now);

        AccountResponse response = AccountMapper.toResponse(account);

        assertNotNull(response);
        assertEquals(id.toString(), response.id());
        assertEquals("Gabriel Jesus", response.holderName());
        assertEquals(new BigDecimal("1500.00"), response.balance());
        assertEquals(now, response.createdAt());
    }
}
