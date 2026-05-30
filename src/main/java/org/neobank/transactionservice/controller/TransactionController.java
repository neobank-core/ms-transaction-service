package org.neobank.transactionservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.neobank.transactionservice.dto.CreateTransactionRequest;
import org.neobank.transactionservice.dto.DepositRequest;
import org.neobank.transactionservice.dto.TransactionResponse;
import org.neobank.transactionservice.entity.Transaction;
import org.neobank.transactionservice.mapper.TransactionMapper;
import org.neobank.transactionservice.service.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid DepositRequest request) {
        return ResponseEntity.ok(transactionMapper.toResponse(
                transactionService.deposit(jwt.getSubject(), request)));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my")
    public ResponseEntity<Page<TransactionResponse>> getMyTransactions(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable) {
        return ResponseEntity.ok(
                transactionService.getMyTransactions(jwt.getSubject(), pageable)
                        .map(transactionMapper::toResponse));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(transactionMapper.toResponse(
                transactionService.getById(id, jwt.getSubject())));
    }
}


