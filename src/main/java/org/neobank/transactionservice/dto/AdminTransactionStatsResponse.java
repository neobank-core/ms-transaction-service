package org.neobank.transactionservice.dto;

import java.math.BigDecimal;

public record AdminTransactionStatsResponse(
        BigDecimal todayTransactionVolume,
        long todayTransactionCount,
        long failedTransactionCount
) {}
