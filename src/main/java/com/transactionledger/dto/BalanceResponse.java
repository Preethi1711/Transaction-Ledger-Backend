package com.transactionledger.dto;

import java.math.BigDecimal;

public record BalanceResponse(
        Long accountId,
        BigDecimal balance
) {
}
