package com.corebank.digital_banking.application.account;

import com.corebank.digital_banking.application.account.dto.AccountResponse;
import com.corebank.digital_banking.application.account.dto.CreateAccountRequest;
import com.corebank.digital_banking.application.exception.InvalidArgumentException;
import com.corebank.digital_banking.domain.account.Account;
import com.corebank.digital_banking.domain.account.AccountRepository;
import com.corebank.digital_banking.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Create Account Use Case Unit Tests")
class CreateAccountUseCaseTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private CreateAccountUseCase createAccountUseCase;

    @Test
    @DisplayName("Should successfully execute account creation workflow")
    void shouldCreateAccountSuccessfully() {
        CreateAccountRequest request = new CreateAccountRequest("Gabriel Jesus", new BigDecimal("1500.00"));

        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            return account;
        });
        when(accountRepository.findById(any(java.util.UUID.class))).thenAnswer(invocation -> {
            java.util.UUID id = invocation.getArgument(0);
            return java.util.Optional.of(new Account(id, "Gabriel Jesus", new BigDecimal("1500.00"), java.time.Instant.now()));
        });

        AccountResponse response = createAccountUseCase.execute(request);

        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals("Gabriel Jesus", response.holderName());
        assertEquals(new BigDecimal("1500.00"), response.balance());
        assertNotNull(response.createdAt());

        verify(accountRepository, times(1)).save(any(Account.class));
        verify(accountRepository, times(1)).findById(any(java.util.UUID.class));
    }

    @Test
    @DisplayName("Should throw InvalidArgumentException when initial balance exceeds database physical ceiling")
    void shouldThrowInvalidArgumentExceptionWhenInitialBalanceExceedsMaximumLimit() {
        BigDecimal excessiveBalance = new BigDecimal("99999999999999.99991");
        
        org.junit.jupiter.api.Assertions.assertThrows(InvalidArgumentException.class, () -> {
            new CreateAccountRequest("Gabriel Jesus", excessiveBalance);
        });
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when domain account balance exceeds maximum limit")
    void shouldThrowBusinessRuleExceptionWhenDomainAccountBalanceExceedsMaximumLimit() {
        java.util.UUID id = java.util.UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();
        BigDecimal excessiveBalance = new BigDecimal("99999999999999.99991");

        org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleException.class, () -> {
            new Account(id, "Gabriel Jesus", excessiveBalance, now);
        });
    }
}
