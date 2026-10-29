package com.transactionledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @Schema(example = "Household", description = "Account name. Initial balance is always set to 0 by the server.")
        @NotBlank(message = "Account name is required")
        @Size(max = 100, message = "Account name must be at most 100 characters")
        String name
) {
}
