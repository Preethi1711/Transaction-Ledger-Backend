package com.transactionledger.dto;

import com.transactionledger.entity.Transaction;
import com.transactionledger.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        Long accountId,
        TransactionType type,
        BigDecimal amount,
        String description,
        String referenceId,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getReferenceId(),
                transaction.getCreatedAt()
        );
    }
}
