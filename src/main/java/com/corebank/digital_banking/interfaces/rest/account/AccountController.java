package com.corebank.digital_banking.interfaces.rest.account;

import com.corebank.digital_banking.application.account.CreateAccountUseCase;
import com.corebank.digital_banking.application.account.dto.AccountResponse;
import com.corebank.digital_banking.application.account.dto.CreateAccountRequest;
import com.corebank.digital_banking.interfaces.exception.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Accounts", description = "Operations related to bank accounts")
@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final CreateAccountUseCase createAccountUseCase;

    public AccountController(CreateAccountUseCase createAccountUseCase) {
        this.createAccountUseCase = createAccountUseCase;
    }

    @Operation(summary = "Create a new bank account", description = "Creates a new bank account with the specified holder name and initial balance.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Account created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or validation constraint violations",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/create")
    public ResponseEntity<AccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
        AccountResponse response = createAccountUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
