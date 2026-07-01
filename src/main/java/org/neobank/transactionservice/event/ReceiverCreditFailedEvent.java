package org.neobank.transactionservice.event;

import java.math.BigDecimal;
import java.util.UUID;

public record ReceiverCreditFailedEvent(
    UUID transactionId,
    UUID senderAccountId,
    BigDecimal amount,
    String currency,
    String reason
) {}
