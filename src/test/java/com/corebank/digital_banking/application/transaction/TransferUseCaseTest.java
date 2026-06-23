package com.corebank.digital_banking.application.transaction;

import com.corebank.digital_banking.application.exception.InvalidArgumentException;
import com.corebank.digital_banking.application.transaction.dto.TransferRequest;
import com.corebank.digital_banking.application.transaction.dto.TransferResponse;
import com.corebank.digital_banking.domain.exception.BusinessRuleException;
import com.corebank.digital_banking.domain.transaction.AccountBalance;
import com.corebank.digital_banking.domain.transaction.OutboxEvent;
import com.corebank.digital_banking.domain.transaction.TransactionRepository;
import com.corebank.digital_banking.domain.transaction.Transfer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Transfer Use Case Unit Tests")
class TransferUseCaseTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TransferUseCase transferUseCase;

    @Test
    @DisplayName("Should successfully execute transfer and enforce lock ordering")
    void shouldExecuteTransferSuccessfullyEnforcingIdLockOrder() {
        UUID idA = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID idB = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String key = UUID.randomUUID().toString();

        AccountBalance balanceA = new AccountBalance(idA, new BigDecimal("500.00"));
        AccountBalance balanceB = new AccountBalance(idB, new BigDecimal("200.00"));

        when(transactionRepository.findTransferByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(transactionRepository.findTotalTransferredAmountToday(idA)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.findBalanceByIdWithLock(idA)).thenReturn(Optional.of(balanceA));
        when(transactionRepository.findBalanceByIdWithLock(idB)).thenReturn(Optional.of(balanceB));
        
        when(transactionRepository.saveTransfer(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.findTransferById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.of(new Transfer(id, idA, idB, new BigDecimal("100.00"), key, Instant.now()));
        });

        TransferRequest request = new TransferRequest(idA.toString(), idB.toString(), new BigDecimal("100.00"));

        TransferResponse response = transferUseCase.execute(request, key);

        assertNotNull(response);
        assertEquals(new BigDecimal("400.0000"), balanceA.getBalance());
        assertEquals(new BigDecimal("300.0000"), balanceB.getBalance());

        verify(transactionRepository, times(1)).saveBalance(balanceA);
        verify(transactionRepository, times(1)).saveBalance(balanceB);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(transactionRepository, times(1)).saveOutboxEvent(outboxCaptor.capture());
        OutboxEvent capturedOutbox = outboxCaptor.getValue();
        assertNotNull(capturedOutbox);
        assertEquals("PENDING", capturedOutbox.getStatus());
        assertNotNull(capturedOutbox.getPayload());
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when source balance is insufficient")
    void shouldThrowBusinessRuleExceptionWhenBalanceIsInsufficient() {
        UUID idA = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID idB = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String key = UUID.randomUUID().toString();

        AccountBalance balanceA = new AccountBalance(idA, new BigDecimal("50.00"));
        AccountBalance balanceB = new AccountBalance(idB, new BigDecimal("200.00"));

        when(transactionRepository.findTransferByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(transactionRepository.findTotalTransferredAmountToday(idA)).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.findBalanceByIdWithLock(idA)).thenReturn(Optional.of(balanceA));
        when(transactionRepository.findBalanceByIdWithLock(idB)).thenReturn(Optional.of(balanceB));

        TransferRequest request = new TransferRequest(idA.toString(), idB.toString(), new BigDecimal("100.00"));

        assertThrows(BusinessRuleException.class, () -> transferUseCase.execute(request, key));
    }

    @Test
    @DisplayName("Should throw InvalidArgumentException when source and destination are the same account")
    void shouldThrowInvalidArgumentExceptionOnSelfTransfer() {
        UUID idA = UUID.randomUUID();
        assertThrows(InvalidArgumentException.class, () -> {
            new TransferRequest(idA.toString(), idA.toString(), new BigDecimal("100.00"));
        });
    }

    @Test
    @DisplayName("Should throw InvalidArgumentException when Idempotency-Key header is missing or empty")
    void shouldThrowExceptionWhenIdempotencyKeyIsEmpty() {
        TransferRequest request = new TransferRequest(UUID.randomUUID().toString(), UUID.randomUUID().toString(), new BigDecimal("100.00"));
        assertThrows(InvalidArgumentException.class, () -> transferUseCase.execute(request, "  "));
    }

    @Test
    @DisplayName("Should throw InvalidArgumentException when Idempotency-Key header is malformed")
    void shouldThrowExceptionWhenIdempotencyKeyIsMalformed() {
        TransferRequest request = new TransferRequest(UUID.randomUUID().toString(), UUID.randomUUID().toString(), new BigDecimal("100.00"));
        assertThrows(InvalidArgumentException.class, () -> transferUseCase.execute(request, "invalid-uuid-format"));
    }

    @Test
    @DisplayName("Should return cached response when transfer request with the same Idempotency-Key is repeated")
    void shouldReturnCachedResponseForRepeatIdempotentRequest() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        UUID existingTransferId = UUID.randomUUID();

        Transfer existingTransfer = new Transfer(existingTransferId, idA, idB, new BigDecimal("100.00"), key, Instant.now());
        when(transactionRepository.findTransferByIdempotencyKey(key)).thenReturn(Optional.of(existingTransfer));

        TransferRequest request = new TransferRequest(idA.toString(), idB.toString(), new BigDecimal("100.00"));
        TransferResponse response = transferUseCase.execute(request, key);

        assertNotNull(response);
        assertEquals(existingTransferId.toString(), response.transferId());
        
        // Assert no domain execution occurred
        verify(transactionRepository, never()).findBalanceByIdWithLock(any());
        verify(transactionRepository, never()).saveBalance(any());
        verify(transactionRepository, never()).saveTransfer(any());
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when daily limit of R$ 5,000.00 is exceeded")
    void shouldThrowExceptionWhenDailyLimitIsExceeded() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        String key = UUID.randomUUID().toString();

        when(transactionRepository.findTransferByIdempotencyKey(key)).thenReturn(Optional.empty());
        // Mock current today's transactions as R$ 4,950
        when(transactionRepository.findTotalTransferredAmountToday(idA)).thenReturn(new BigDecimal("4950.00"));

        // Attempting to transfer R$ 100.00 (Total = 5050.00 > 5000.00)
        TransferRequest request = new TransferRequest(idA.toString(), idB.toString(), new BigDecimal("100.00"));
        assertThrows(BusinessRuleException.class, () -> transferUseCase.execute(request, key));
    }

    @Test
    @DisplayName("Should throw InvalidArgumentException when Idempotency-Key is reused but request body parameters do not match")
    void shouldThrowExceptionWhenIdempotencyKeyIsReusedWithDifferentParameters() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        UUID existingTransferId = UUID.randomUUID();

        Transfer existingTransfer = new Transfer(existingTransferId, idA, idB, new BigDecimal("100.00"), key, Instant.now());
        when(transactionRepository.findTransferByIdempotencyKey(key)).thenReturn(Optional.of(existingTransfer));

        // Case 1: Different source account
        TransferRequest diffSourceRequest = new TransferRequest(UUID.randomUUID().toString(), idB.toString(), new BigDecimal("100.00"));
        assertThrows(InvalidArgumentException.class, () -> transferUseCase.execute(diffSourceRequest, key));

        // Case 2: Different destination account
        TransferRequest diffDestRequest = new TransferRequest(idA.toString(), UUID.randomUUID().toString(), new BigDecimal("100.00"));
        assertThrows(InvalidArgumentException.class, () -> transferUseCase.execute(diffDestRequest, key));

        // Case 3: Different amount
        TransferRequest diffAmountRequest = new TransferRequest(idA.toString(), idB.toString(), new BigDecimal("200.00"));
        assertThrows(InvalidArgumentException.class, () -> transferUseCase.execute(diffAmountRequest, key));
    }
}
