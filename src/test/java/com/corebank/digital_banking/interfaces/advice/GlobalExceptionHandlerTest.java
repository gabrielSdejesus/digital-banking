package com.corebank.digital_banking.interfaces.advice;

import com.corebank.digital_banking.application.exception.EntityNotFoundException;
import com.corebank.digital_banking.application.exception.InvalidArgumentException;
import com.corebank.digital_banking.domain.exception.BusinessRuleException;
import com.corebank.digital_banking.interfaces.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Global Exception Handler Rest Advice Tests")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("Should successfully handle InvalidArgumentException")
    void shouldHandleInvalidArgumentException() throws Exception {
        mockMvc.perform(get("/test/invalid-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid input parameters provided"))
                .andExpect(jsonPath("$.code").value("BANK-001"))
                .andExpect(jsonPath("$.details", hasSize(1)))
                .andExpect(jsonPath("$.details[0].field").value("amount"))
                .andExpect(jsonPath("$.details[0].message").value("Amount must be positive"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should successfully handle MethodArgumentNotValidException")
    void shouldHandleMethodArgumentNotValidException() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed for one or more fields."))
                .andExpect(jsonPath("$.code").value("BANK-001"))
                .andExpect(jsonPath("$.details", hasSize(1)))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").value("Name is required"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should successfully handle EntityNotFoundException")
    void shouldHandleEntityNotFoundException() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Account with ID abc not found"))
                .andExpect(jsonPath("$.code").value("BANK-003"))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should successfully handle BusinessRuleException")
    void shouldHandleBusinessRuleException() throws Exception {
        mockMvc.perform(get("/test/business-rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.message").value("Insufficient funds for this transfer"))
                .andExpect(jsonPath("$.code").value("BANK-002"))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should successfully handle QueryTimeoutException")
    void shouldHandleQueryTimeoutException() throws Exception {
        mockMvc.perform(get("/test/timeout"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.status").value(423))
                .andExpect(jsonPath("$.error").value("Locked"))
                .andExpect(jsonPath("$.message").value("Database lock request timed out"))
                .andExpect(jsonPath("$.code").value("BANK-004"))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should successfully handle generic Exception and sanitize exception details in response")
    void shouldHandleGenericExceptionAndSanitizeMessage() throws Exception {
        mockMvc.perform(get("/test/generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message")
                        .value("An unexpected internal error occurred on our servers. Please try again later."))
                .andExpect(jsonPath("$.code").value("BANK-999"))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should successfully handle MethodArgumentTypeMismatchException")
    void shouldHandleMethodArgumentTypeMismatchException() throws Exception {
        mockMvc.perform(get("/test/type-mismatch").param("number", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Parameter 'number' should be of type 'int'"))
                .andExpect(jsonPath("$.code").value("BANK-001"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should successfully handle HttpRequestMethodNotSupportedException")
    void shouldHandleHttpRequestMethodNotSupportedException() throws Exception {
        mockMvc.perform(get("/test/validation"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.message").value("Request method 'GET' is not supported"))
                .andExpect(jsonPath("$.code").value("BANK-005"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}

@RestController
class TestController {

    @GetMapping("/test/invalid-argument")
    public void throwInvalidArgument() {
        throw new InvalidArgumentException(
                "Invalid input parameters provided",
                List.of(new InvalidArgumentException.ValidationError("amount", "Amount must be positive")));
    }

    @PostMapping("/test/validation")
    public void throwValidation(@Valid @RequestBody TestRequest request) {
    }

    @GetMapping("/test/not-found")
    public void throwNotFound() {
        throw new EntityNotFoundException("Account with ID abc not found");
    }

    @GetMapping("/test/business-rule")
    public void throwBusinessRule() {
        throw new BusinessRuleException("Insufficient funds for this transfer");
    }

    @GetMapping("/test/timeout")
    public void throwTimeout() {
        throw new QueryTimeoutException("Database lock request timed out", new Exception("timeout"));
    }

    @GetMapping("/test/generic")
    public void throwGeneric() {
        throw new NullPointerException("Simulated Null Pointer Exception");
    }

    @GetMapping("/test/type-mismatch")
    public void throwTypeMismatch(@RequestParam int number) {
    }
}

class TestRequest {
    @NotBlank(message = "Name is required")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}