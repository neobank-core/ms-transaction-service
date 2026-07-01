package org.neobank.transactionservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull
        UUID senderCardId,
        @NotBlank
        String receiverCardNumber,
        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be > 0")
        BigDecimal amount,
        @NotBlank
        String currency
) {}
