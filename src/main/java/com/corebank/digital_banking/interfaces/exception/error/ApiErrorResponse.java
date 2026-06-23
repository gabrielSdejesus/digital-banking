package com.corebank.digital_banking.interfaces.exception.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String code,
    List<ApiErrorDetail> details
) {
    public ApiErrorResponse(Instant timestamp, int status, String error, String message, String code) {
        this(timestamp, status, error, message, code, null);
    }
}
