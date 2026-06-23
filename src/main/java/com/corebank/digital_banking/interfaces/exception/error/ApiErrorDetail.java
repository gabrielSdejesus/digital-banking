package com.corebank.digital_banking.interfaces.exception.error;

public record ApiErrorDetail(
    String field,
    String message
) {}
