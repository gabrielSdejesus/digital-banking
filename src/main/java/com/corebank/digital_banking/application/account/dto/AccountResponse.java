package com.corebank.digital_banking.application.account.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        String id,
        String holderName,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal balance,
        Instant createdAt) {
}
