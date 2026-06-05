package org.neobank.transactionservice.service;

import io.getunleash.Unleash;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neobank.transactionservice.client.AccountServiceClient;
import org.neobank.transactionservice.client.CardServiceClient;
import org.neobank.transactionservice.client.dto.BalanceAdjustmentRequest;
import org.neobank.transactionservice.client.dto.BalanceOperationResponse;
import org.neobank.transactionservice.client.dto.InternalCardResponse;
import org.neobank.transactionservice.config.FeatureFlags;
import org.neobank.transactionservice.dto.CreateTransactionRequest;
import org.neobank.transactionservice.dto.DepositRequest;
import org.neobank.transactionservice.entity.Transaction;
import org.neobank.transactionservice.entity.TransactionLedger;
import org.neobank.transactionservice.enums.EntryType;
import org.neobank.transactionservice.enums.TransactionStatus;
import org.neobank.transactionservice.enums.TransactionType;
import org.neobank.transactionservice.event.TransactionCompletedEvent;
import org.neobank.transactionservice.event.TransactionFailedEvent;
import org.neobank.transactionservice.event.TransactionInitiatedEvent;
import org.neobank.transactionservice.exception.TransactionNotFoundException;
import org.neobank.transactionservice.exception.TransactionValidationException;
import org.neobank.transactionservice.publisher.TransactionEventPublisher;
import org.neobank.transactionservice.repository.TransactionLedgerRepository;
import org.neobank.transactionservice.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionEventPublisher publisher;
    private final TransactionLedgerRepository ledgerRepository;
    private final CardServiceClient cardServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final Unleash unleash;

    @Transactional
    public Transaction createTransaction(String initiatorUserId, String idempotencyKey, CreateTransactionRequest request) {
        if (unleash.isEnabled(FeatureFlags.MAINTENANCE_MODE)) {
            throw new TransactionValidationException("System is under maintenance");
        }

        if (unleash.isEnabled(FeatureFlags.TRANSFER_DAILY_LIMIT)) {
            if (request.amount().compareTo(new BigDecimal("10000")) > 0) {
                throw new TransactionValidationException("Daily limit exceeded");
            }
        }

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

        InternalCardResponse senderCard = cardServiceClient.getCard(request.senderCardId());
        InternalCardResponse receiverCard = cardServiceClient.getCard(request.receiverCardId());

        validateCardForTransfer(senderCard, initiatorUserId, true);
        validateCardForTransfer(receiverCard, initiatorUserId, false);

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

        BalanceAdjustmentRequest adjustment = new BalanceAdjustmentRequest(request.amount(), request.currency());

        try {
            BalanceOperationResponse debitResult = accountServiceClient.debit(
                    senderCard.accountId(),
                    adjustment
            );

            try {
                BalanceOperationResponse creditResult = accountServiceClient.credit(
                        receiverCard.accountId(),
                        adjustment
                );

                saved.setStatus(TransactionStatus.COMPLETED);
                transactionRepository.save(saved);

                saveLedgerEntry(saved, EntryType.DEBIT, senderCard.accountId(), request.amount().negate(), debitResult.balance());
                saveLedgerEntry(saved, EntryType.CREDIT, receiverCard.accountId(), request.amount(), creditResult.balance());

                publisher.publishCompleted(new TransactionCompletedEvent(
                        saved.getId(),
                        initiatorUserId,
                        saved.getSenderCardId(),
                        saved.getReceiverCardId(),
                        saved.getAmount(),
                        saved.getCurrency(),
                        LocalDateTime.now()
                ));

                publisher.publishCompleted(new TransactionCompletedEvent(
                        saved.getId(),
                        receiverCard.userId(),
                        saved.getSenderCardId(),
                        saved.getReceiverCardId(),
                        saved.getAmount(),
                        saved.getCurrency(),
                        LocalDateTime.now()
                ));
            } catch (Exception creditError) {
                log.error("Credit failed, compensating debit for transaction {}", saved.getId(), creditError);
                accountServiceClient.credit(senderCard.accountId(), adjustment);
                failTransaction(saved, "Credit failed: " + creditError.getMessage());
            }
        } catch (Exception debitError) {
            log.error("Debit failed for transaction {}", saved.getId(), debitError);
            failTransaction(saved, "Debit failed: " + debitError.getMessage());
        }

        return saved;
    }

    @Transactional
    public Transaction deposit(String keycloakUserId, DepositRequest request) {
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionValidationException("Amount must be greater than zero");
        }

        UUID userId = UUID.fromString(keycloakUserId);
        BalanceOperationResponse account = accountServiceClient.getCheckingAccount(userId);
        String currency = request.currency() != null ? request.currency() : account.currency();
        BalanceAdjustmentRequest adjustment = new BalanceAdjustmentRequest(request.amount(), currency);

        BalanceOperationResponse creditResult = accountServiceClient.credit(account.accountId(), adjustment);

        Transaction transaction = Transaction.builder()
                .keycloakUserId(keycloakUserId)
                .amount(request.amount())
                .currency(request.currency())
                .status(TransactionStatus.COMPLETED)
                .type(TransactionType.DEPOSIT)
                .referenceId(UUID.randomUUID().toString())
                .build();

        Transaction saved = transactionRepository.save(transaction);

        saveLedgerEntry(saved, EntryType.CREDIT, account.accountId(), request.amount(), creditResult.balance());

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
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found or access denied"));
    }

    @Transactional
    public Transaction getById(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + id));
    }

    public List<Transaction> getTransactionsByAccount(UUID accountId) {
        return transactionRepository.findAllByAccountId(accountId);
    }

    public void failPendingTransactionsForCard(UUID cardId, String reason) {
        var pending = transactionRepository.findBySenderCardIdAndStatus(cardId, TransactionStatus.PENDING);
        pending.forEach(tx -> failTransaction(tx, reason));
    }

    @Transactional
    public Transaction reverseTransaction(UUID id) {
        Transaction transaction = getById(id);

        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new TransactionValidationException("Only COMPLETED transactions can be reversed");
        }

        if (transaction.getType() == TransactionType.TRANSFER) {
            InternalCardResponse senderCard = cardServiceClient.getCard(transaction.getSenderCardId());
            InternalCardResponse receiverCard = cardServiceClient.getCard(transaction.getReceiverCardId());
            BalanceAdjustmentRequest adjustment = new BalanceAdjustmentRequest(transaction.getAmount(), transaction.getCurrency());

            accountServiceClient.debit(receiverCard.accountId(), adjustment);
            accountServiceClient.credit(senderCard.accountId(), adjustment);
        } else if (transaction.getType() == TransactionType.DEPOSIT) {
            UUID userId = UUID.fromString(transaction.getKeycloakUserId());
            BalanceOperationResponse account = accountServiceClient.getCheckingAccount(userId);
            BalanceAdjustmentRequest adjustment = new BalanceAdjustmentRequest(transaction.getAmount(), transaction.getCurrency());
            accountServiceClient.debit(account.accountId(), adjustment);
        }

        transaction.setStatus(TransactionStatus.FAILED);
        Transaction saved = transactionRepository.save(transaction);
        
        publisher.publishFailed(new TransactionFailedEvent(
                saved.getId(),
                saved.getKeycloakUserId(),
                "Transaction reversed by admin",
                LocalDateTime.now()
        ));

        return saved;
    }

    private void validateCardForTransfer(InternalCardResponse card, String initiatorUserId, boolean mustBeSender) {
        if (!"ACTIVE".equals(card.status())) {
            throw new TransactionValidationException("Card is not active: " + card.id());
        }
        if (mustBeSender && !initiatorUserId.equals(card.userId())) {
            throw new TransactionValidationException("Sender card does not belong to the current user");
        }
    }

    private void failTransaction(Transaction transaction, String reason) {
        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(transaction);
        publisher.publishFailed(new TransactionFailedEvent(
                transaction.getId(),
                transaction.getKeycloakUserId(),
                reason,
                LocalDateTime.now()
        ));
    }

    private void saveLedgerEntry(
            Transaction transaction,
            EntryType entryType,
            UUID accountId,
            BigDecimal amount,
            BigDecimal balanceAfter
    ) {
        ledgerRepository.save(TransactionLedger.builder()
                .transaction(transaction)
                .entryType(entryType)
                .accountId(accountId)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .build());
    }
}
