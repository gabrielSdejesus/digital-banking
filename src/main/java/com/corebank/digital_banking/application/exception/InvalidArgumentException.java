package com.corebank.digital_banking.application.exception;

import java.util.Collections;
import java.util.List;

public class InvalidArgumentException extends RuntimeException {
    private final List<ValidationError> errors;

    public InvalidArgumentException(String message) {
        super(message);
        this.errors = Collections.emptyList();
    }

    public InvalidArgumentException(String message, String field, String errorMsg) {
        super(message);
        this.errors = List.of(new ValidationError(field, errorMsg));
    }

    public InvalidArgumentException(String message, List<ValidationError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public record ValidationError(String field, String message) {}
}
