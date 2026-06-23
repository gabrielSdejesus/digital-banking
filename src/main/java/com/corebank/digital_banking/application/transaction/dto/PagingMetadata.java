package com.corebank.digital_banking.application.transaction.dto;

public record PagingMetadata(
    String nextCursor,
    boolean hasMore
) {}
