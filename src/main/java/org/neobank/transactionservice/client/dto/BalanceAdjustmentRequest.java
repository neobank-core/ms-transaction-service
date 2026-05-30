package org.neobank.transactionservice.client.dto;

import java.math.BigDecimal;

public record BalanceAdjustmentRequest(BigDecimal amount, String currency) {
}
