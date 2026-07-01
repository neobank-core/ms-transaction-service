package org.neobank.transactionservice.client;

import org.neobank.transactionservice.client.dto.InternalCardResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class CardServiceClientFallbackFactory implements FallbackFactory<CardServiceClient> {

    @Override
    public CardServiceClient create(Throwable cause) {
        return new CardServiceClient() {
            @Override
            public InternalCardResponse getCard(UUID id) {
                if (cause instanceof feign.FeignException.NotFound) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient card not found: " + id);
                } else if (cause instanceof feign.FeignException.BadRequest) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid card request");
                }
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, 
                        "Card Service is temporarily unavailable. Circuit breaker opened. Reason: " + cause.getMessage()
                );
            }

            @Override
            public InternalCardResponse getCardByNumber(String cardNumber) {
                if (cause instanceof feign.FeignException.NotFound) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient card not found: " + cardNumber);
                } else if (cause instanceof feign.FeignException.BadRequest) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid card request");
                }
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, 
                        "Card Service is temporarily unavailable. Circuit breaker opened. Reason: " + cause.getMessage()
                );
            }
        };
    }
}
