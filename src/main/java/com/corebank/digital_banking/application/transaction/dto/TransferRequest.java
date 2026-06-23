package com.corebank.digital_banking.application.transaction.dto;

import com.corebank.digital_banking.application.exception.InvalidArgumentException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @Schema(description = "UUID of the source account", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED, example = "a2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e")
        String sourceAccountId,
        @Schema(description = "UUID of the destination account", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED, example = "b3c4d5e6-f7a8-9b0c-1d2e-3f4a5b6c7d8e")
        String destinationAccountId,
        @Schema(description = "Amount to transfer. Must be greater than zero.", minimum = "0.0", exclusiveMinimum = true, maximum = "99999999999999.9999", requiredMode = Schema.RequiredMode.REQUIRED, example = "250.50")
        BigDecimal amount) {

    public TransferRequest {
        if (sourceAccountId == null || sourceAccountId.trim().isEmpty()) {
            throw new InvalidArgumentException("Source account ID is required", "sourceAccountId",
                    "Source account ID cannot be null or empty");
        }
        try {
            UUID.fromString(sourceAccountId);
        } catch (IllegalArgumentException e) {
            throw new InvalidArgumentException("Invalid source account ID format", "sourceAccountId",
                    "Source account ID must be a valid UUID");
        }

        if (destinationAccountId == null || destinationAccountId.trim().isEmpty()) {
            throw new InvalidArgumentException("Destination account ID is required", "destinationAccountId",
                    "Destination account ID cannot be null or empty");
        }
        try {
            UUID.fromString(destinationAccountId);
        } catch (IllegalArgumentException e) {
            throw new InvalidArgumentException("Invalid destination account ID format", "destinationAccountId",
                    "Destination account ID must be a valid UUID");
        }

        if (sourceAccountId.trim().equals(destinationAccountId.trim())) {
            throw new InvalidArgumentException("Self-transfer not allowed", "destinationAccountId",
                    "Source and destination accounts must be different");
        }

        if (amount == null) {
            throw new InvalidArgumentException("Transfer amount is required", "amount",
                    "Amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidArgumentException("Invalid transfer amount", "amount",
                    "Amount must be strictly greater than zero");
        }
        BigDecimal maxLimit = new BigDecimal("99999999999999.9999");
        if (amount.compareTo(maxLimit) > 0) {
            throw new InvalidArgumentException("Amount exceeds maximum limit", "amount",
                    "Amount must be less than or equal to 99999999999999.9999");
        }
        try {
            amount.setScale(4, java.math.RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidArgumentException("Invalid decimal scale", "amount",
                    "The amount must have at most 4 decimal places");
        }
    }
}
