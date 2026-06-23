package com.corebank.digital_banking.application.account.dto;

import com.corebank.digital_banking.application.exception.InvalidArgumentException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @Schema(description = "Account holder's full name", minLength = 3, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED, example = "John Doe")
        String holderName,
        @Schema(description = "Initial balance for the account", minimum = "0.0", maximum = "99999999999999.9999", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000.00")
        BigDecimal initialBalance) {
    public CreateAccountRequest {
        if (holderName == null || holderName.trim().isEmpty()) {
            throw new InvalidArgumentException("Holder name is required", "holderName",
                    "The holder name must not be null or empty");
        }
        if (holderName.trim().length() < 3 || holderName.trim().length() > 100) {
            throw new InvalidArgumentException("Invalid holder name size", "holderName",
                    "The holder name must be between 3 and 100 characters");
        }
        if (initialBalance == null) {
            throw new InvalidArgumentException("Initial balance is required", "initialBalance",
                    "The initial balance must not be null");
        }
        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidArgumentException("Negative initial balance", "initialBalance",
                    "The initial balance cannot be negative");
        }
        BigDecimal maxLimit = new BigDecimal("99999999999999.9999");
        if (initialBalance.compareTo(maxLimit) > 0) {
            throw new InvalidArgumentException("Initial balance exceeds maximum limit", "initialBalance",
                    "The initial balance must be less than or equal to 99999999999999.9999");
        }
        try {
            initialBalance.setScale(4, java.math.RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidArgumentException("Invalid decimal scale", "initialBalance",
                    "The initial balance must have at most 4 decimal places");
        }
    }
}
