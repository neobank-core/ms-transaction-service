package org.neobank.transactionservice.event;

import java.math.BigDecimal;
import java.util.UUID;

public record SenderDebitedEvent(
    UUID transactionId,
    UUID senderAccountId,
    UUID receiverAccountId,
    BigDecimal amount,
    String currency,
    BigDecimal senderBalanceAfter
) {}
