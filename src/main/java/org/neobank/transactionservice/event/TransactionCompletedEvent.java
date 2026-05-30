package org.neobank.transactionservice.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionCompletedEvent(
        UUID transactionId,
        String keycloakUserId,
        UUID senderCardId,
        UUID receiverCardId,
        BigDecimal amount,
        String currency,
        LocalDateTime completedAt
) {}