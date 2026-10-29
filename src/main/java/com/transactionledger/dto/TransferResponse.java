package com.transactionledger.dto;

import java.math.BigDecimal;

public record TransferResponse(
        Long fromAccountId,
        BigDecimal fromBalance,
        Long toAccountId,
        BigDecimal toBalance,
        TransactionResponse debit,
        TransactionResponse credit
) {
}
