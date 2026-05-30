package org.neobank.transactionservice.client.dto;

import java.util.UUID;

public record InternalCardResponse(
        UUID id,
        UUID accountId,
        String userId,
        String status,
        String cardNumberMasked
) {
}
