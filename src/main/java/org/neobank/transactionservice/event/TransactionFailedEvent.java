package org.neobank.transactionservice.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionFailedEvent(
        UUID transactionId,
        String reason,
        LocalDateTime failedAt
) {}