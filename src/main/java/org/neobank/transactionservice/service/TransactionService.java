package org.neobank.transactionservice.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.neobank.transactionservice.dto.CreateTransactionRequest;
import org.neobank.transactionservice.dto.DepositRequest;
import org.neobank.transactionservice.entity.Transaction;
import org.neobank.transactionservice.entity.TransactionLedger;
import org.neobank.transactionservice.enums.EntryType;
import org.neobank.transactionservice.enums.TransactionStatus;
import org.neobank.transactionservice.enums.TransactionType;
import org.neobank.transactionservice.event.TransactionCompletedEvent;
import org.neobank.transactionservice.event.TransactionInitiatedEvent;
import org.neobank.transactionservice.exception.TransactionNotFoundException;
import org.neobank.transactionservice.exception.TransactionValidationException;
import org.neobank.transactionservice.publisher.TransactionEventPublisher;
import org.neobank.transactionservice.repository.TransactionLedgerRepository;
import org.neobank.transactionservice.repository.TransactionRepository;
import org.springframework.boot.health.application.ReadinessStateHealthIndicator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionEventPublisher publisher;
    private final TransactionLedgerRepository ledgerRepository;

    @Transactional
    public Transaction createTransaction(String initiatorUserId, String idempotencyKey, CreateTransactionRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new TransactionValidationException("X-Idempotency-Key header is required");
        }

        Optional<Transaction> existing = transactionRepository.findByReferenceId(idempotencyKey);

        if (existing.isPresent()) {
            return existing.get();
        }

        if (request.senderCardId().equals(request.receiverCardId())) {
            throw new TransactionValidationException("Sender and receiver cannot be the same");
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionValidationException("Amount must be greater than zero");
        }

        Transaction transaction = Transaction.builder()
                .keycloakUserId(initiatorUserId)
                .senderCardId(request.senderCardId())
                .receiverCardId(request.receiverCardId())
                .amount(request.amount())
                .currency(request.currency())
                .status(TransactionStatus.PENDING)
                .type(TransactionType.TRANSFER)
                .referenceId(idempotencyKey)
                .build();

        Transaction saved = transactionRepository.save(transaction);

        publisher.publishInitiated(new TransactionInitiatedEvent(
                saved.getId(),
                initiatorUserId,
                saved.getSenderCardId(),
                saved.getReceiverCardId(),
                saved.getAmount(),
                saved.getCurrency()
        ));

        saved.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(saved);

        ledgerRepository.save(TransactionLedger.builder()
                .transaction(saved)
                .entryType(EntryType.DEBIT)
                .accountId(saved.getSenderCardId())
                .amount(saved.getAmount().negate())
                .balanceAfter(BigDecimal.ZERO)
                .build());

        ledgerRepository.save(TransactionLedger.builder()
                .transaction(saved)
                .entryType(EntryType.CREDIT)
                .accountId(saved.getReceiverCardId())
                .amount(saved.getAmount())
                .balanceAfter(BigDecimal.ZERO)
                .build());

        publisher.publishCompleted(new TransactionCompletedEvent(
                saved.getId(), initiatorUserId,
                saved.getSenderCardId(), saved.getReceiverCardId(),
                saved.getAmount(), saved.getCurrency(),
                LocalDateTime.now()
        ));

        return saved;
    }

    @Transactional
    public Transaction deposit(String keycloakUserId, DepositRequest request) {
        Transaction transaction = Transaction.builder()
                .keycloakUserId(keycloakUserId)
                .amount(request.amount())
                .currency(request.currency())
                .status(TransactionStatus.COMPLETED)
                .type(TransactionType.DEPOSIT)
                .referenceId(UUID.randomUUID().toString())
                .build();

        Transaction saved = transactionRepository.save(transaction);

        publisher.publishCompleted(new TransactionCompletedEvent(
                saved.getId(),
                keycloakUserId,
                null,
                null,
                saved.getAmount(),
                saved.getCurrency(),
                LocalDateTime.now()
        ));

        return saved;
    }

    @Transactional
    public Page<Transaction> getMyTransactions(String keycloakUserId, Pageable pageable) {
        return transactionRepository.findByKeycloakUserId(keycloakUserId, pageable);
    }

    @Transactional
    public Transaction getById(UUID id, String keycloakUserId) {
        return transactionRepository.findByIdAndKeycloakUserId(id, keycloakUserId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + id));
    }

    @Transactional
    public Transaction getById(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + id));
    }
}

