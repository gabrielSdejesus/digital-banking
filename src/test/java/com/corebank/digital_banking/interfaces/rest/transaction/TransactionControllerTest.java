package com.corebank.digital_banking.interfaces.rest.transaction;

import com.corebank.digital_banking.application.exception.InvalidArgumentException;
import com.corebank.digital_banking.application.transaction.GeneratePdfStatementUseCase;
import com.corebank.digital_banking.application.transaction.GetStatementUseCase;
import com.corebank.digital_banking.application.transaction.TransferUseCase;
import com.corebank.digital_banking.application.transaction.dto.TransferResponse;
import com.corebank.digital_banking.interfaces.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Transaction Controller REST Endpoint Tests")
class TransactionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TransferUseCase transferUseCase;

    @Mock
    private GetStatementUseCase getStatementUseCase;

    @Mock
    private GeneratePdfStatementUseCase generatePdfStatementUseCase;

    @InjectMocks
    private TransactionController transactionController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(transactionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Should return HTTP 200 OK when money transfer is successfully completed")
    void shouldReturnOkWhenPostTransferIsValid() throws Exception {
        UUID transferId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();
        TransferResponse response = new TransferResponse(transferId.toString(), sourceId.toString(), destId.toString(), new BigDecimal("100.00"), Instant.now());

        when(transferUseCase.execute(any(), any())).thenReturn(response);

        mockMvc.perform(post("/v1/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"" + sourceId + "\",\"destinationAccountId\":\"" + destId + "\",\"amount\":100.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(transferId.toString()))
                .andExpect(jsonPath("$.amount").value(100.00));
    }

    @Test
    @DisplayName("Should return HTTP 200 OK and paged statement response for valid statement range request")
    void shouldReturnOkForStatementRangeRequest() throws Exception {
        UUID accountId = UUID.randomUUID();
        com.corebank.digital_banking.application.transaction.dto.PagingMetadata paging =
                new com.corebank.digital_banking.application.transaction.dto.PagingMetadata(null, false);
        com.corebank.digital_banking.application.transaction.dto.PagedTransferResponse response =
                new com.corebank.digital_banking.application.transaction.dto.PagedTransferResponse(Collections.emptyList(), paging);

        when(getStatementUseCase.execute(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(response);

        mockMvc.perform(get("/v1/accounts/" + accountId + "/statements/range")
                        .param("startDate", "2026-06-01T00:00:00Z")
                        .param("endDate", "2026-06-15T00:00:00Z")
                        .param("pageSize", "10")
                        .param("cursor", "someCursor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.paging.nextCursor").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.paging.hasMore").value(false));
    }

    @Test
    @DisplayName("Should return HTTP 200 OK and PDF content type for valid yearly statement request")
    void shouldReturnPdfForStatementYearRequest() throws Exception {
        UUID accountId = UUID.randomUUID();
        byte[] fakePdf = new byte[]{1, 2, 3, 4};

        when(generatePdfStatementUseCase.execute(accountId.toString(), 2025)).thenReturn(fakePdf);

        mockMvc.perform(get("/v1/accounts/" + accountId + "/statements/year")
                        .param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statement-2025.pdf"))
                .andExpect(content().bytes(fakePdf));
    }

    @Test
    @DisplayName("Should return HTTP 400 Bad Request when transfer amount has more than 4 decimal places")
    void shouldReturnBadRequestWhenTransferAmountHasTooManyDecimals() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();

        mockMvc.perform(post("/v1/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"" + sourceId + "\",\"destinationAccountId\":\"" + destId + "\",\"amount\":100.12345}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("BANK-001"))
                .andExpect(jsonPath("$.details[0].field").value("amount"))
                .andExpect(jsonPath("$.details[0].message").value("The amount must have at most 4 decimal places"));
    }

    @Test
    @DisplayName("Should return HTTP 400 Bad Request and JSON error body when PDF statement fails with invalid size")
    void shouldReturnJsonErrorWhenPdfStatementFails() throws Exception {
        String invalidAccountId = "short-id";

        when(generatePdfStatementUseCase.execute(eq(invalidAccountId), anyInt()))
                .thenThrow(new InvalidArgumentException(
                        "Invalid account ID size", "accountId", "Account ID must be exactly 36 characters"));

        mockMvc.perform(get("/v1/accounts/" + invalidAccountId + "/statements/year")
                        .param("year", "2025"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BANK-001"))
                .andExpect(jsonPath("$.details[0].field").value("accountId"))
                .andExpect(jsonPath("$.details[0].message").value("Account ID must be exactly 36 characters"));
    }

    @Test
    @DisplayName("Should return HTTP 400 Bad Request when Idempotency-Key header is missing")
    void shouldReturnBadRequestWhenIdempotencyKeyIsMissing() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();

        mockMvc.perform(post("/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"" + sourceId + "\",\"destinationAccountId\":\"" + destId + "\",\"amount\":100.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return HTTP 400 Bad Request when Idempotency-Key header is malformed")
    void shouldReturnBadRequestWhenIdempotencyKeyIsMalformed() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID destId = UUID.randomUUID();

        when(transferUseCase.execute(any(), eq("invalid-uuid")))
                .thenThrow(new InvalidArgumentException("Invalid Idempotency-Key format", "idempotencyKey", "Idempotency-Key must be a valid UUID v4"));

        mockMvc.perform(post("/v1/transfers")
                        .header("Idempotency-Key", "invalid-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"" + sourceId + "\",\"destinationAccountId\":\"" + destId + "\",\"amount\":100.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BANK-001"))
                .andExpect(jsonPath("$.details[0].field").value("idempotencyKey"))
                .andExpect(jsonPath("$.details[0].message").value("Idempotency-Key must be a valid UUID v4"));
    }
}
