package org.neobank.transactionservice.client;

import org.neobank.transactionservice.client.dto.InternalCardResponse;
import org.neobank.transactionservice.config.FeignInternalConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "card-service",
        url = "${card-service.url}",
        configuration = FeignInternalConfig.class
)
public interface CardServiceClient {

    @GetMapping("/api/internal/cards/{id}")
    InternalCardResponse getCard(@PathVariable UUID id);
}
