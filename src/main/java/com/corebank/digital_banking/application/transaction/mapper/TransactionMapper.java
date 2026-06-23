package com.corebank.digital_banking.application.transaction.mapper;

import com.corebank.digital_banking.application.transaction.dto.TransferResponse;
import com.corebank.digital_banking.domain.transaction.Transfer;

public final class TransactionMapper {

    private TransactionMapper() {
    }

    public static TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(
                transfer.getId().toString(),
                transfer.getSourceAccountId().toString(),
                transfer.getDestinationAccountId().toString(),
                transfer.getAmount(),
                transfer.getCreatedAt());
    }
}
