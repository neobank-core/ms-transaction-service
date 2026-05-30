package org.neobank.transactionservice.service;

import lombok.RequiredArgsConstructor;
import org.neobank.transactionservice.dto.CreateTransactionRequest;
import org.neobank.transactionservice.entity.Transaction;
import org.neobank.transactionservice.enums.TransactionStatus;
import org.neobank.transactionservice.enums.TransactionType;
import org.neobank.transactionservice.event.TransactionInitiatedEvent;
import org.neobank.transactionservice.exception.TransactionValidationException;
import org.neobank.transactionservice.publisher.TransactionEventPublisher;
import org.neobank.transactionservice.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionEventPublisher publisher;

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
                .senderCardId(request.senderCardId())
                .receiverCardId(request.receiverCardId())
                .amount(request.amount())
                .currency(request.currency())
                .status(TransactionStatus.PENDING)
                .type(TransactionType.TRANSFER)
                .referenceId(idempotencyKey)
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        publisher.publish(new TransactionInitiatedEvent(
                savedTransaction.getId(),
                initiatorUserId,
                savedTransaction.getSenderCardId(),
                savedTransaction.getReceiverCardId(),
                savedTransaction.getAmount(),
                savedTransaction.getCurrency()
        ));

        return savedTransaction;
    }
}

