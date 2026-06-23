package com.corebank.digital_banking.interfaces.rest.transaction;

import com.corebank.digital_banking.application.transaction.GeneratePdfStatementUseCase;
import com.corebank.digital_banking.application.transaction.GetStatementUseCase;
import com.corebank.digital_banking.application.transaction.TransferUseCase;
import com.corebank.digital_banking.application.transaction.dto.PagedTransferResponse;
import com.corebank.digital_banking.application.transaction.dto.TransferRequest;
import com.corebank.digital_banking.application.transaction.dto.TransferResponse;
import com.corebank.digital_banking.interfaces.exception.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Transactions", description = "Operations related to money transfers and statements")
@RestController
public class TransactionController {

    private final TransferUseCase transferUseCase;
    private final GetStatementUseCase getStatementUseCase;
    private final GeneratePdfStatementUseCase generatePdfStatementUseCase;

    public TransactionController(
            TransferUseCase transferUseCase,
            GetStatementUseCase getStatementUseCase,
            GeneratePdfStatementUseCase generatePdfStatementUseCase) {
        this.transferUseCase = transferUseCase;
        this.getStatementUseCase = getStatementUseCase;
        this.generatePdfStatementUseCase = generatePdfStatementUseCase;
    }

    @Operation(summary = "Transfer money between accounts", description = "Executes a money transfer between two accounts with pessimistic transaction locks and transaction outbox logging.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transfer completed successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransferResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload, malformed UUID formats, or missing/invalid Idempotency-Key",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Source or destination account not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Business rule violation (e.g. self-transfer, insufficient funds, or daily limit exceeded)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "423", description = "Pessimistic lock timeout / concurrency contention",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/v1/transfers")
    public ResponseEntity<TransferResponse> createTransfer(
            @Parameter(
                name = "Idempotency-Key",
                in = ParameterIn.HEADER,
                description = "Chave de idempotência única (UUID v4) para evitar reprocessamento em caso de retentativas",
                required = true,
                schema = @Schema(type = "string", format = "uuid")
            )
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody TransferRequest request) {
        TransferResponse response = transferUseCase.execute(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "Get account transaction statement within a date range", description = "Retrieves a list of all transfers involving the specified account within a date range up to 90 days using keyset (cursor-based) pagination.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statement retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PagedTransferResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range parameters, invalid cursor format, missing values, or malformed UUID/date formats",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected internal server error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/v1/accounts/{accountId}/statements/range")
    public ResponseEntity<PagedTransferResponse> getStatementRange(
            @PathVariable String accountId,
            @Parameter(description = "Start date in ISO-8601 UTC format (e.g. 0000-00-00T00:00:00Z)", example = "0000-00-00T00:00:00Z")
            @RequestParam String startDate,
            @Parameter(description = "End date in ISO-8601 UTC format (e.g. 0000-00-00T00:00:00Z)", example = "0000-00-00T00:00:00Z")
            @RequestParam String endDate,
            @Parameter(description = "Page size (default 20, max 100)", example = "20")
            @RequestParam(required = false) Integer pageSize,
            @Parameter(description = "Pagination cursor (opaque Base64 string)")
            @RequestParam(required = false) String cursor) {
        PagedTransferResponse response = getStatementUseCase.execute(accountId, startDate, endDate, pageSize, cursor);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Generate annual PDF statement", description = "Generates and downloads a PDF document of the specified account's transaction history for a full calendar year.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PDF statement generated and downloaded successfully",
                     content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE, schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Invalid query parameter values or malformed UUID formats",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Business rule violation (e.g. transfer history has more than 5,000 records)",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected internal server error",
                     content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping(value = "/v1/accounts/{accountId}/statements/year")
    public ResponseEntity<byte[]> getPdfStatement(
            @PathVariable String accountId,
            @RequestParam int year) {
        byte[] pdf = generatePdfStatementUseCase.execute(accountId, year);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statement-" + year + ".pdf")
                .body(pdf);
    }
}
