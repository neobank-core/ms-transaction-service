package org.neobank.transactionservice.event;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionInitiatedEvent(
        UUID transactionId,
        String initiatorUserId,
        UUID senderAccountId,
        UUID receiverAccountId,
        BigDecimal amount,
        String currency
) {}
