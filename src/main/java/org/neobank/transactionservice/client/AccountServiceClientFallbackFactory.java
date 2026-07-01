package org.neobank.transactionservice.client;

import org.neobank.transactionservice.client.dto.BalanceAdjustmentRequest;
import org.neobank.transactionservice.client.dto.BalanceOperationResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class AccountServiceClientFallbackFactory implements FallbackFactory<AccountServiceClient> {
    @Override
    public AccountServiceClient create(Throwable cause) {
        return new AccountServiceClient() {
            @Override
            public BalanceOperationResponse getCheckingAccount(UUID userId) {
                if (cause instanceof feign.FeignException.NotFound) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found for user: " + userId);
                }
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account Service is unavailable: " + cause.getMessage());
            }
            @Override
            public BalanceOperationResponse debit(UUID id, BalanceAdjustmentRequest request) {
                if (cause instanceof feign.FeignException.BadRequest || cause instanceof feign.FeignException.UnprocessableEntity) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debit failed: " + cause.getMessage());
                } else if (cause instanceof feign.FeignException.NotFound) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + id);
                }
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account Service is unavailable: " + cause.getMessage());
            }
            @Override
            public BalanceOperationResponse credit(UUID id, BalanceAdjustmentRequest request) {
                if (cause instanceof feign.FeignException.BadRequest || cause instanceof feign.FeignException.UnprocessableEntity) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credit failed: " + cause.getMessage());
                } else if (cause instanceof feign.FeignException.NotFound) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + id);
                }
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account Service is unavailable: " + cause.getMessage());
            }
        };
    }
}
