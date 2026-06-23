package com.corebank.digital_banking.interfaces.exception;

import com.corebank.digital_banking.application.exception.EntityNotFoundException;
import com.corebank.digital_banking.application.exception.InvalidArgumentException;
import com.corebank.digital_banking.domain.exception.BusinessRuleException;
import com.corebank.digital_banking.interfaces.exception.error.ApiErrorDetail;
import com.corebank.digital_banking.interfaces.exception.error.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private void writeJsonResponse(HttpServletResponse response, HttpStatus status, ApiErrorResponse body) {
        try {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(body));
        } catch (IOException e) {
            log.error("Failed to write JSON error response", e);
        }
    }

    @ExceptionHandler(InvalidArgumentException.class)
    public void handleInvalidArgumentException(InvalidArgumentException ex, HttpServletResponse response) {
        List<ApiErrorDetail> details = null;
        if (ex.getErrors() != null && !ex.getErrors().isEmpty()) {
            details = ex.getErrors().stream()
                    .map(error -> new ApiErrorDetail(error.field(), error.message()))
                    .collect(Collectors.toList());
        }
        ApiErrorResponse apiResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                "BANK-001",
                details
        );
        writeJsonResponse(response, HttpStatus.BAD_REQUEST, apiResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public void handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletResponse response) {
        List<ApiErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorDetail(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList());

        ApiErrorResponse apiResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed for one or more fields.",
                "BANK-001",
                details
        );
        writeJsonResponse(response, HttpStatus.BAD_REQUEST, apiResponse);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public void handleEntityNotFoundException(EntityNotFoundException ex, HttpServletResponse response) {
        ApiErrorResponse apiResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                "BANK-003"
        );
        writeJsonResponse(response, HttpStatus.NOT_FOUND, apiResponse);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public void handleBusinessRuleException(BusinessRuleException ex, HttpServletResponse response) {
        ApiErrorResponse apiResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage(),
                "BANK-002"
        );
        writeJsonResponse(response, HttpStatus.UNPROCESSABLE_ENTITY, apiResponse);
    }

    @ExceptionHandler(QueryTimeoutException.class)
    public void handleQueryTimeoutException(QueryTimeoutException ex, HttpServletResponse response) {
        ApiErrorResponse apiResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.LOCKED.value(),
                HttpStatus.LOCKED.getReasonPhrase(),
                ex.getMessage(),
                "BANK-004"
        );
        writeJsonResponse(response, HttpStatus.LOCKED, apiResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public void handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, HttpServletResponse response) {
        Throwable cause = ex.getCause();
        if (cause instanceof ValueInstantiationException valueInstantiationException) {
            Throwable rootCause = valueInstantiationException.getCause();
            if (rootCause instanceof InvalidArgumentException invalidArgumentException) {
                handleInvalidArgumentException(invalidArgumentException, response);
                return;
            }
        }
        ApiErrorResponse apiResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Malformed JSON request body.",
                "BANK-001"
        );
        writeJsonResponse(response, HttpStatus.BAD_REQUEST, apiResponse);
    }

    @ExceptionHandler(Exception.class)
    public void handleGenericException(Exception ex, HttpServletResponse response) {
        log.error("An unexpected error occurred: ", ex);
        ApiErrorResponse apiResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected internal error occurred on our servers. Please try again later.",
                "BANK-999"
        );
        writeJsonResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, apiResponse);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public void handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex, HttpServletResponse response) {
        String message = String.format("Parameter '%s' should be of type '%s'", ex.getName(), 
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        ApiErrorResponse apiResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                "BANK-001"
        );
        writeJsonResponse(response, HttpStatus.BAD_REQUEST, apiResponse);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public void handleServletRequestBindingException(ServletRequestBindingException ex, HttpServletResponse response) {
        ApiErrorResponse apiResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                "BANK-001"
        );
        writeJsonResponse(response, HttpStatus.BAD_REQUEST, apiResponse);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public void handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex, HttpServletResponse response) {
        ApiErrorResponse apiResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(),
                ex.getMessage(),
                "BANK-005"
        );
        writeJsonResponse(response, HttpStatus.METHOD_NOT_ALLOWED, apiResponse);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResourceFoundException(NoResourceFoundException ex, HttpServletResponse response) {
        log.warn("Resource not found: {}", ex.getMessage());
        ApiErrorResponse apiResponse = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                "BANK-003"
        );
        writeJsonResponse(response, HttpStatus.NOT_FOUND, apiResponse);
    }
}
