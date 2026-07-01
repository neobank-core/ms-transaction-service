package org.neobank.transactionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TranResp(
        UUID id,
        BigDecimal amount,
        String currency,
        String status,
        String type,
        LocalDateTime createdAt
) {
}
