package org.neobank.transactionservice.event;

import java.math.BigDecimal;
import java.util.UUID;

public record CompensateSenderDebitEvent(
    UUID transactionId,
    UUID senderAccountId,
    BigDecimal amount,
    String currency
) {}
