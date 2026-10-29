package com.transactionledger.dto;

import java.util.List;

public record TransactionPageResponse(
        List<TransactionResponse> transactions,
        int page,
        int pageSize,
        long totalRecords,
        int totalPages
) {
}
