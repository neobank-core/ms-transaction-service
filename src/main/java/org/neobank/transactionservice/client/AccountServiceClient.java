package org.neobank.transactionservice.client;

import org.neobank.transactionservice.client.dto.BalanceAdjustmentRequest;
import org.neobank.transactionservice.client.dto.BalanceOperationResponse;
import org.neobank.transactionservice.config.FeignInternalConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(
        name = "account-service",
        configuration = FeignInternalConfig.class,
        fallbackFactory = AccountServiceClientFallbackFactory.class
)
public interface AccountServiceClient {

    @GetMapping("/api/internal/accounts/user/{userId}/checking")
    BalanceOperationResponse getCheckingAccount(@PathVariable("userId") UUID userId);

    @PostMapping("/api/internal/accounts/{id}/debit")
    BalanceOperationResponse debit(@PathVariable("id") UUID id, @RequestBody BalanceAdjustmentRequest request);

    @PostMapping("/api/internal/accounts/{id}/credit")
    BalanceOperationResponse credit(@PathVariable("id") UUID id, @RequestBody BalanceAdjustmentRequest request);
}
