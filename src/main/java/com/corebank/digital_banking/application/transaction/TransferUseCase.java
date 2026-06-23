package com.corebank.digital_banking.application.transaction;

import com.corebank.digital_banking.application.exception.EntityNotFoundException;
import com.corebank.digital_banking.application.exception.InvalidArgumentException;
import com.corebank.digital_banking.application.transaction.dto.TransferRequest;
import com.corebank.digital_banking.application.transaction.dto.TransferResponse;
import com.corebank.digital_banking.application.transaction.mapper.TransactionMapper;
import com.corebank.digital_banking.domain.exception.BusinessRuleException;
import com.corebank.digital_banking.domain.transaction.AccountBalance;
import com.corebank.digital_banking.domain.transaction.OutboxEvent;
import com.corebank.digital_banking.domain.transaction.TransactionRepository;
import com.corebank.digital_banking.domain.transaction.Transfer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class TransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(TransferUseCase.class);
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    public TransferUseCase(TransactionRepository transactionRepository, ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(timeout = 5)
    public TransferResponse execute(TransferRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new InvalidArgumentException("Idempotency-Key is required", "idempotencyKey", "Idempotency-Key header cannot be null or empty");
        }
        if (idempotencyKey.trim().length() != 36) {
            throw new InvalidArgumentException("Invalid Idempotency-Key size", "idempotencyKey", "Idempotency-Key must be exactly 36 characters");
        }
        try {
            UUID.fromString(idempotencyKey);
        } catch (IllegalArgumentException e) {
            throw new InvalidArgumentException("Invalid Idempotency-Key format", "idempotencyKey", "Idempotency-Key must be a valid UUID v4");
        }

        java.util.Optional<Transfer> existing = transactionRepository.findTransferByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Transfer existingTransfer = existing.get();
            if (!existingTransfer.getSourceAccountId().toString().equals(request.sourceAccountId())
                    || !existingTransfer.getDestinationAccountId().toString().equals(request.destinationAccountId())
                    || existingTransfer.getAmount().compareTo(request.amount()) != 0) {
                throw new InvalidArgumentException("Idempotency-Key collision", "idempotencyKey",
                        "This idempotency key has already been used for a transfer with different parameters.");
            }
            log.info("Idempotent request matched for key {}. Returning existing transfer response.", idempotencyKey);
            return TransactionMapper.toResponse(existingTransfer);
        }

        UUID sourceId = UUID.fromString(request.sourceAccountId());
        UUID destId = UUID.fromString(request.destinationAccountId());

        java.math.BigDecimal dailyTotal = transactionRepository.findTotalTransferredAmountToday(sourceId);
        java.math.BigDecimal newTotal = dailyTotal.add(request.amount());
        if (newTotal.compareTo(new java.math.BigDecimal("5000.0000")) > 0) {
            throw new BusinessRuleException("Daily transfer limit exceeded. Max daily limit is R$ 5,000.00");
        }

        UUID firstId = sourceId.compareTo(destId) < 0 ? sourceId : destId;
        UUID secondId = sourceId.compareTo(destId) < 0 ? destId : sourceId;

        AccountBalance firstBalance = transactionRepository.findBalanceByIdWithLock(firstId)
                .orElseThrow(() -> new EntityNotFoundException("Account with ID " + firstId + " not found"));

        AccountBalance secondBalance = transactionRepository.findBalanceByIdWithLock(secondId)
                .orElseThrow(() -> new EntityNotFoundException("Account with ID " + secondId + " not found"));

        AccountBalance sourceBalance = sourceId.equals(firstBalance.getAccountId()) ? firstBalance : secondBalance;
        AccountBalance destBalance = destId.equals(firstBalance.getAccountId()) ? firstBalance : secondBalance;

        sourceBalance.debit(request.amount());
        destBalance.credit(request.amount());

        transactionRepository.saveBalance(firstBalance);
        transactionRepository.saveBalance(secondBalance);

        UUID transferId = UUID.randomUUID();
        Instant now = Instant.now();
        Transfer transfer = new Transfer(transferId, sourceId, destId, request.amount(), idempotencyKey, now);
        transactionRepository.saveTransfer(transfer);

        String payloadJson;
        try {
            Map<String, Object> payloadMap = Map.of(
                    "transferId", transferId.toString(),
                    "sourceAccountId", sourceId.toString(),
                    "destinationAccountId", destId.toString(),
                    "amount", request.amount().setScale(4, RoundingMode.HALF_DOWN).toString(),
                    "createdAt", now.toString());
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            log.error(e.getMessage());
            throw new RuntimeException("Failed transfer account.");
        }

        OutboxEvent outboxEvent = new OutboxEvent(null, transferId.toString(), payloadJson, "PENDING", 0, now);
        transactionRepository.saveOutboxEvent(outboxEvent);

        Transfer fetchedTransfer = transactionRepository.findTransferById(transferId)
                .orElseThrow(() -> new EntityNotFoundException("Transfer with ID " + transferId + " not found"));

        return TransactionMapper.toResponse(fetchedTransfer);
    }
}
