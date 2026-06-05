package org.neobank.transactionservice.controller;

import lombok.RequiredArgsConstructor;
import org.neobank.transactionservice.dto.TransactionResponse;
import org.neobank.transactionservice.entity.Transaction;
import org.neobank.transactionservice.mapper.TransactionMapper;
import org.neobank.transactionservice.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions/internal")
@RequiredArgsConstructor
public class InternalTransactionController {

    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<TransactionResponse>> getTransactionsByAccount(@PathVariable UUID accountId) {
        List<TransactionResponse> response = transactionService.getTransactionsByAccount(accountId)
                .stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<TransactionResponse> reverseTransaction(@PathVariable UUID id) {
        Transaction transaction = transactionService.reverseTransaction(id);
        return ResponseEntity.ok(transactionMapper.toResponse(transaction));
    }
}
