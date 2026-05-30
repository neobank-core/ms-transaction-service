package org.neobank.transactionservice.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceOperationResponse(
        UUID accountId,
        UUID userId,
        BigDecimal balance,
        String currency
) {
}
