package com.transactionledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
        @Schema(example = "1", description = "Account that is debited")
        @NotNull(message = "Source account id is required")
        Long fromAccountId,

        @Schema(example = "2", description = "Account that is credited")
        @NotNull(message = "Destination account id is required")
        Long toAccountId,

        @Schema(example = "30.00")
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 2 decimal places")
        BigDecimal amount,

        @Schema(example = "Shared expense")
        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @Schema(example = "TRF-1001", description = "Stored on both the debit and the credit. Must be unused on each account.")
        @NotBlank(message = "Reference id is required")
        @Size(max = 25, message = "Reference id must be at most 25 characters")
        String referenceId
) {
}
