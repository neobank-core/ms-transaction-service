package org.neobank.transactionservice.client;

import org.neobank.transactionservice.config.FeignInternalConfig;
import org.neobank.transactionservice.dto.KycStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "user-service",
        configuration = FeignInternalConfig.class,
        fallbackFactory = UserServiceClientFallbackFactory.class
)
@Component
public interface UserServiceClient {

    @GetMapping("/api/internal/users/{id}/kyc-status")
    KycStatusResponse getKycStatus(@PathVariable("id") String keycloakUserId);
}
