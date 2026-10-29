package com.transactionledger.controller;

import com.transactionledger.dto.ErrorResponse;
import com.transactionledger.dto.TransferRequest;
import com.transactionledger.dto.TransferResponse;
import com.transactionledger.logging.AppLogger;
import com.transactionledger.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transfers", description = "Move money from one account to another in a single transaction")
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private static final Logger log = AppLogger.get(TransferController.class);

    private final TransactionService transactionService;

    public TransferController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(
            summary = "Transfer money between two accounts",
            description = "Debits the source account and credits the destination account together. "
                    + "If either side fails, neither balance changes and neither ledger row is kept."
    )
    @ApiResponse(responseCode = "201", description = "Transfer completed")
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Reference id already used on one of the accounts",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Source account has insufficient balance",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        log.info("Entering transfer fromAccountId={} toAccountId={} referenceId={}",
                request.fromAccountId(), request.toAccountId(), request.referenceId());
        try {
            TransferResponse response = transactionService.transfer(request);
            log.info("Exiting transfer fromAccountId={} toAccountId={}", response.fromAccountId(), response.toAccountId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException ex) {
            log.info("Exiting transfer with {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }
}
