package com.transactionledger.controller;

import com.transactionledger.dto.AccountResponse;
import com.transactionledger.dto.BalanceResponse;
import com.transactionledger.dto.CreateAccountRequest;
import com.transactionledger.dto.ErrorResponse;
import com.transactionledger.logging.AppLogger;
import com.transactionledger.service.AccountService;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Accounts", description = "Create accounts and read the current balance")
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private static final Logger log = AppLogger.get(AccountController.class);

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(
            summary = "Create an account",
            description = "Creates an account. The initial balance is always 0 and is assigned by the server."
    )
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        log.info("Entering createAccount");
        try {
            AccountResponse response = accountService.createAccount(request);
            log.info("Exiting createAccount accountId={}", response.id());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException ex) {
            log.info("Exiting createAccount with {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    @Operation(summary = "Get the current account balance")
    @ApiResponse(responseCode = "200", description = "Balance returned")
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{accountId}/balance")
    public BalanceResponse getBalance(
            @Parameter(description = "Account id", example = "1") @PathVariable Long accountId
    ) {
        log.info("Entering getBalance accountId={}", accountId);
        try {
            BalanceResponse response = accountService.getBalance(accountId);
            log.info("Exiting getBalance accountId={}", accountId);
            return response;
        } catch (RuntimeException ex) {
            log.info("Exiting getBalance accountId={} with {}: {}", accountId, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }
}
