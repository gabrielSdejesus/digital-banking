package com.corebank.digital_banking.application.transaction.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        String transferId,
        String sourceAccountId,
        String destinationAccountId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal amount,
        Instant createdAt) {
}
