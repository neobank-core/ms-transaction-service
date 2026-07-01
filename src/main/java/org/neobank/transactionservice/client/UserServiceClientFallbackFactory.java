package org.neobank.transactionservice.client;

import org.neobank.transactionservice.dto.KycStatusResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {
            @Override
            public KycStatusResponse getKycStatus(String keycloakUserId) {
                // Return default NONE status if user-service is down
                return new KycStatusResponse("NONE");
            }
        };
    }
}
