package com.corebank.digital_banking.interfaces.rest.account;

import com.corebank.digital_banking.application.account.CreateAccountUseCase;
import com.corebank.digital_banking.application.account.dto.AccountResponse;
import com.corebank.digital_banking.application.account.dto.CreateAccountRequest;
import com.corebank.digital_banking.interfaces.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account Controller REST Endpoint Tests")
class AccountControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CreateAccountUseCase createAccountUseCase;

    @InjectMocks
    private AccountController accountController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(accountController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("Should return HTTP 201 Created when account creation request is valid")
    void shouldReturnCreatedWhenValidRequest() throws Exception {
        String accountId = UUID.randomUUID().toString();
        AccountResponse response = new AccountResponse(accountId, "Gabriel Jesus", new BigDecimal("1500.00"),
                Instant.now());

        when(createAccountUseCase.execute(any(CreateAccountRequest.class))).thenReturn(response);

        mockMvc.perform(post("/v1/accounts/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holderName\":\"Gabriel Jesus\",\"initialBalance\":1500.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(accountId))
                .andExpect(jsonPath("$.holderName").value("Gabriel Jesus"))
                .andExpect(jsonPath("$.balance").value(1500.00))
                .andExpect(jsonPath("$.createdAt").exists());

        verify(createAccountUseCase, times(1)).execute(any(CreateAccountRequest.class));
    }

    @Test
    @DisplayName("Should return HTTP 400 Bad Request when holder name size is too short")
    void shouldReturnBadRequestWhenHolderNameIsShort() throws Exception {
        mockMvc.perform(post("/v1/accounts/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holderName\":\"G\",\"initialBalance\":1500.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("BANK-001"))
                .andExpect(jsonPath("$.details", hasSize(1)))
                .andExpect(jsonPath("$.details[0].field").value("holderName"))
                .andExpect(
                        jsonPath("$.details[0].message").value("The holder name must be between 3 and 100 characters"));

        verifyNoInteractions(createAccountUseCase);
    }

    @Test
    @DisplayName("Should return HTTP 400 Bad Request when initial balance is negative")
    void shouldReturnBadRequestWhenBalanceIsNegative() throws Exception {
        mockMvc.perform(post("/v1/accounts/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holderName\":\"Gabriel Jesus\",\"initialBalance\":-50.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("BANK-001"))
                .andExpect(jsonPath("$.details", hasSize(1)))
                .andExpect(jsonPath("$.details[0].field").value("initialBalance"))
                .andExpect(jsonPath("$.details[0].message").value("The initial balance cannot be negative"));

        verifyNoInteractions(createAccountUseCase);
    }

    @Test
    @DisplayName("Should return HTTP 400 Bad Request when initial balance exceeds maximum limit")
    void shouldReturnBadRequestWhenBalanceExceedsMaximumLimit() throws Exception {
        mockMvc.perform(post("/v1/accounts/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holderName\":\"Gabriel Jesus\",\"initialBalance\":99999999999999.99991}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("BANK-001"))
                .andExpect(jsonPath("$.details", hasSize(1)))
                .andExpect(jsonPath("$.details[0].field").value("initialBalance"))
                .andExpect(jsonPath("$.details[0].message").value("The initial balance must be less than or equal to 99999999999999.9999"));

        verifyNoInteractions(createAccountUseCase);
    }

    @Test
    @DisplayName("Should return HTTP 400 Bad Request when initial balance has more than 4 decimal places")
    void shouldReturnBadRequestWhenBalanceHasTooManyDecimals() throws Exception {
        mockMvc.perform(post("/v1/accounts/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holderName\":\"Gabriel Jesus\",\"initialBalance\":100.12345}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("BANK-001"))
                .andExpect(jsonPath("$.details", hasSize(1)))
                .andExpect(jsonPath("$.details[0].field").value("initialBalance"))
                .andExpect(jsonPath("$.details[0].message").value("The initial balance must have at most 4 decimal places"));

        verifyNoInteractions(createAccountUseCase);
    }
}
