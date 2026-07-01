package org.neobank.transactionservice.event;

import java.util.UUID;
import java.math.BigDecimal;

public record ReceiverCreditedEvent(
    UUID transactionId,
    UUID receiverAccountId,
    BigDecimal amount,
    String currency,
    BigDecimal receiverBalanceAfter
) {}
