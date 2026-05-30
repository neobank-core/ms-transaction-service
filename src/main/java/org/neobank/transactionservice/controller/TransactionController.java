package org.neobank.transactionservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.neobank.transactionservice.dto.CreateTransactionRequest;
import org.neobank.transactionservice.dto.TransactionResponse;
import org.neobank.transactionservice.entity.Transaction;
import org.neobank.transactionservice.mapper.TransactionMapper;
import org.neobank.transactionservice.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> createTransaction(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid CreateTransactionRequest request
    ) {
        Transaction transaction = transactionService.createTransaction(jwt.getSubject(), idempotencyKey, request);
        return ResponseEntity.ok(transactionMapper.toResponse(transaction));
    }
}


