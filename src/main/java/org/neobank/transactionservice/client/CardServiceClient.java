package org.neobank.transactionservice.client;

import org.neobank.transactionservice.client.dto.InternalCardResponse;
import org.neobank.transactionservice.config.FeignInternalConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "card-service",
        configuration = FeignInternalConfig.class,
        fallbackFactory = CardServiceClientFallbackFactory.class
)
public interface CardServiceClient {

    @GetMapping("/api/internal/cards/{id}")
    InternalCardResponse getCard(@PathVariable("id") UUID id);

    @GetMapping("/api/internal/cards/by-number/{cardNumber}")
    InternalCardResponse getCardByNumber(@PathVariable("cardNumber") String cardNumber);
}
