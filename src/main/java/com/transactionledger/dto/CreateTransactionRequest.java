package com.transactionledger.dto;

import com.transactionledger.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        @Schema(example = "CREDIT", description = "CREDIT or DEBIT")
        @NotNull(message = "Transaction type is required")
        TransactionType type,

        @Schema(example = "25.50")
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 2 decimal places")
        BigDecimal amount,

        @Schema(example = "Opening deposit")
        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @Schema(example = "REF-1001")
        @NotBlank(message = "Reference id is required")
        @Size(max = 25, message = "Reference id must be at most 25 characters")
        String referenceId
) {
}
