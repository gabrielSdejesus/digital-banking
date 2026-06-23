package com.corebank.digital_banking.application.transaction.dto;

import java.util.List;

public record PagedTransferResponse(
    List<TransferResponse> data,
    PagingMetadata paging
) {}
