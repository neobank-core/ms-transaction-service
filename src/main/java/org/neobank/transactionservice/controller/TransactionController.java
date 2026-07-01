package org.neobank.transactionservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.neobank.transactionservice.dto.*;
import org.neobank.transactionservice.entity.Transaction;
import org.neobank.transactionservice.enums.TransactionStatus;
import org.neobank.transactionservice.enums.TransactionType;
import org.neobank.transactionservice.mapper.TransactionMapper;
import org.neobank.transactionservice.service.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/transfer")
    public ResponseEntity<TranResp> createTransaction(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid CreateTransactionRequest request
    ) {
        Transaction transaction = transactionService.createTransaction(jwt.getSubject(), idempotencyKey, request);
        return ResponseEntity.ok(transactionMapper.tranResp(transaction, jwt.getSubject()));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid DepositRequest request) {
        return ResponseEntity.ok(transactionMapper.toResponse(
                transactionService.deposit(jwt.getSubject(), idempotencyKey, request), jwt.getSubject()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my")
    public ResponseEntity<PageResponse<TransactionResponse>> getMyTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(value = "search", required = false) String search,
            Pageable pageable) {
        Page<TransactionResponse> page = transactionService.getMyTransactions(jwt.getSubject(), status, type, startDate, endDate, search, pageable)
                .map(tx -> transactionMapper.toResponse(tx, jwt.getSubject()));
        return ResponseEntity.ok(new PageResponse<>(page));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        Transaction transaction = hasRole(jwt, "ADMIN")
                ? transactionService.getById(id)
                : transactionService.getById(id, jwt.getSubject());
        return ResponseEntity.ok(transactionMapper.toResponse(transaction, jwt.getSubject()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<PageResponse<TransactionResponse>> getAllTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(value = "search", required = false) String search,
            Pageable pageable) {
        Page<TransactionResponse> page = transactionService.getAllTransactions(status, type, startDate, endDate, search, pageable)
                .map(tx -> transactionMapper.toResponse(tx, jwt.getSubject()));
        return ResponseEntity.ok(new PageResponse<>(page));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/stats")
    public ResponseEntity<org.neobank.transactionservice.dto.AdminTransactionStatsResponse> getAdminStats() {
        return ResponseEntity.ok(transactionService.getAdminStats());
    }

    private boolean hasRole(Jwt jwt, String role) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return false;
        }
        Object roles = realmAccess.get("roles");
        return roles instanceof List<?> roleList && roleList.contains(role);
    }
}

