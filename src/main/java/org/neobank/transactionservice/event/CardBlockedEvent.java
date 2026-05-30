package org.neobank.transactionservice.event;

import java.util.UUID;

public record CardBlockedEvent(UUID cardId, UUID accountId, String userId) {}