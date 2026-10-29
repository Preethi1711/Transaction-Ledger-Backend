package com.transactionledger.controller;

import com.transactionledger.dto.CreateTransactionRequest;
import com.transactionledger.dto.ErrorResponse;
import com.transactionledger.dto.TransactionPageResponse;
import com.transactionledger.dto.TransactionResponse;
import com.transactionledger.entity.TransactionType;
import com.transactionledger.logging.AppLogger;
import com.transactionledger.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transactions", description = "Create immutable transactions and list account history")
@RestController
@RequestMapping("/api/accounts/{accountId}/transactions")
public class TransactionController {

    private static final Logger log = AppLogger.get(TransactionController.class);

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(
            summary = "Add a credit or debit transaction",
            description = "Credits increase the balance. Debits decrease it when funds are sufficient. "
                    + "referenceId must be unique for the account. The transaction and balance update commit together."
    )
    @ApiResponse(responseCode = "201", description = "Transaction created")
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Duplicate reference id for this account",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Insufficient balance",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<TransactionResponse> addTransaction(
            @Parameter(description = "Account id", example = "1") @PathVariable Long accountId,
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        log.info("Entering addTransaction accountId={} type={} referenceId={}", accountId, request.type(), request.referenceId());
        try {
            TransactionResponse response = transactionService.addTransaction(accountId, request);
            log.info("Exiting addTransaction accountId={} transactionId={}", accountId, response.id());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException ex) {
            log.info("Exiting addTransaction accountId={} with {}: {}", accountId, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    @Operation(
            summary = "List transactions for an account",
            description = "Returns transactions for one account, newest first. "
                    + "Omit type to include both CREDIT and DEBIT. page starts at 1."
    )
    @ApiResponse(responseCode = "200", description = "Transaction page")
    @ApiResponse(responseCode = "400", description = "Invalid type or pagination",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public TransactionPageResponse getHistory(
            @Parameter(description = "Account id", example = "1") @PathVariable Long accountId,
            @Parameter(description = "Optional filter: CREDIT or DEBIT")
            @RequestParam(required = false) TransactionType type,
            @Parameter(description = "Page number, starting at 1", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size from 1 to 100", example = "10")
            @RequestParam(defaultValue = "10") int limit
    ) {
        log.info("Entering getHistory accountId={} type={} page={} limit={}", accountId, type, page, limit);
        try {
            TransactionPageResponse response = transactionService.getHistory(accountId, type, page, limit);
            log.info("Exiting getHistory accountId={} returned={}", accountId, response.transactions().size());
            return response;
        } catch (RuntimeException ex) {
            log.info("Exiting getHistory accountId={} with {}: {}", accountId, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }
}
