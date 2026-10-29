package com.transactionledger.dto;

public record FieldErrorItem(
        String field,
        String message
) {
}
