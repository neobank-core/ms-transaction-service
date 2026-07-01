package org.neobank.transactionservice.service;

import io.getunleash.Unleash;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neobank.transactionservice.client.AccountServiceClient;
import org.neobank.transactionservice.client.CardServiceClient;
import org.neobank.transactionservice.client.UserServiceClient;
import org.neobank.transactionservice.client.dto.BalanceAdjustmentRequest;
import org.neobank.transactionservice.client.dto.BalanceOperationResponse;
import org.neobank.transactionservice.client.dto.InternalCardResponse;
import org.neobank.transactionservice.config.FeatureFlags;
import org.neobank.transactionservice.dto.AdminTransactionStatsResponse;
import org.neobank.transactionservice.dto.CreateTransactionRequest;
import org.neobank.transactionservice.dto.DepositRequest;
import org.neobank.transactionservice.dto.KycStatusResponse;
import org.neobank.transactionservice.entity.Transaction;
import org.neobank.transactionservice.entity.TransactionLedger;
import org.neobank.transactionservice.enums.EntryType;
import org.neobank.transactionservice.enums.TransactionStatus;
import org.neobank.transactionservice.enums.TransactionType;
import org.neobank.transactionservice.event.TransactionCompletedEvent;
import org.neobank.transactionservice.event.TransactionFailedEvent;
import org.neobank.transactionservice.event.TransactionInitiatedEvent;
import org.neobank.transactionservice.exception.KycNotApprovedException;
import org.neobank.transactionservice.exception.TransactionNotFoundException;
import org.neobank.transactionservice.exception.TransactionValidationException;
import org.neobank.transactionservice.publisher.TransactionEventPublisher;
import org.neobank.transactionservice.repository.TransactionLedgerRepository;
import org.neobank.transactionservice.repository.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionLookupService transactionLookupService;
    private final TransactionPersistenceService persistenceService;
    private final TransactionEventPublisher publisher;
    private final TransactionLedgerRepository ledgerRepository;
    private final CardServiceClient cardServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final UserServiceClient userServiceClient;
    private final Unleash unleash;

    @Value("${app.transfer.daily-limit:10000}")
    private BigDecimal defaultDailyLimit;

    public Transaction createTransaction(String initiatorUserId, String idempotencyKey, CreateTransactionRequest request) {
        if (unleash.isEnabled(FeatureFlags.MAINTENANCE_MODE)) {
            throw new TransactionValidationException("System is under maintenance");
        }

        KycStatusResponse kycResponse = userServiceClient.getKycStatus(initiatorUserId);
        if (!"APPROVED".equals(kycResponse.status())) {
            throw new KycNotApprovedException("Cannot perform transfer: KYC is not APPROVED (Current status: " + kycResponse.status() + ")");
        }

        if (unleash.isEnabled(FeatureFlags.TRANSFER_DAILY_LIMIT)) {
            java.time.LocalDateTime todayStart = java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
            BigDecimal dailySum = transactionRepository.getDailyTransferSum(initiatorUserId, todayStart);
            if (dailySum == null) {
                dailySum = BigDecimal.ZERO;
            }
            if (dailySum.add(request.amount()).compareTo(defaultDailyLimit) > 0) {
                BigDecimal remaining = defaultDailyLimit.subtract(dailySum).max(BigDecimal.ZERO);
                throw new TransactionValidationException("Daily limit exceeded. You can only transfer up to " + remaining + " " + request.currency() + " more today.");
            }
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new TransactionValidationException("X-Idempotency-Key header is required");
        }


        InternalCardResponse senderCard = cardServiceClient.getCard(request.senderCardId());
        String normalizedCardNumber = request.receiverCardNumber().replaceAll("\\s+", "");
        InternalCardResponse receiverCard = cardServiceClient.getCardByNumber(normalizedCardNumber);

        if (request.senderCardId().equals(receiverCard.id())) {
            throw new TransactionValidationException("Sender and receiver cannot be the same");
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionValidationException("Amount must be greater than zero");
        }

        validateCardForTransfer(senderCard, initiatorUserId, true);
        validateCardForTransfer(receiverCard, initiatorUserId, false);

        Transaction transaction = Transaction.builder()
                .keycloakUserId(initiatorUserId)
                .senderCardId(request.senderCardId())
                .receiverCardId(receiverCard.id())
                .amount(request.amount())
                .currency(request.currency())
                .status(TransactionStatus.PENDING)
                .type(TransactionType.TRANSFER)
                .referenceId(idempotencyKey)
                .build();

        try {

            Transaction saved = persistenceService.save(transaction);

            publisher.publishInitiated(
                    new TransactionInitiatedEvent(
                            saved.getId(),
                            initiatorUserId,
                            senderCard.accountId(),
                            receiverCard.accountId(),
                            saved.getAmount(),
                            saved.getCurrency()
                    )
            );


            return saved;

        } catch (DataIntegrityViolationException e) {

            log.info("Duplicate idempotency key {}", idempotencyKey);

            return transactionLookupService.findByReferenceId(idempotencyKey);
        }
    }

    public Transaction deposit(String keycloakUserId, String idempotencyKey, DepositRequest request) {
        KycStatusResponse kycResponse = userServiceClient.getKycStatus(keycloakUserId);
        if (!"APPROVED".equals(kycResponse.status())) {
            throw new KycNotApprovedException("Cannot perform transfer: KYC is not APPROVED (Current status: " + kycResponse.status() + ")");
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionValidationException("Amount must be greater than zero");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new TransactionValidationException("X-Idempotency-Key header is required");
        }

        UUID userId = UUID.fromString(keycloakUserId);
        BalanceOperationResponse account = accountServiceClient.getCheckingAccount(userId);
        String currency = request.currency() != null ? request.currency() : account.currency();
        BalanceAdjustmentRequest adjustment = new BalanceAdjustmentRequest(request.amount(), currency);

        Transaction transaction = Transaction.builder()
                .keycloakUserId(keycloakUserId)
                .amount(request.amount())
                .currency(request.currency())
                .status(TransactionStatus.PENDING)
                .type(TransactionType.DEPOSIT)
                .referenceId(idempotencyKey)
                .build();

        Transaction saved;

        try {

            saved = persistenceService.save(transaction);

        } catch (DataIntegrityViolationException e) {

            log.info("Duplicate idempotency key {}", idempotencyKey);

            return transactionLookupService.findByReferenceId(idempotencyKey);
        }


        try {
            BalanceOperationResponse creditResult = accountServiceClient.credit(account.accountId(), adjustment);
            
            saved.setStatus(TransactionStatus.COMPLETED);
            transactionRepository.save(saved);
            
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

        } catch (Exception e) {
            log.error("Deposit failed for transaction {}", saved.getId(), e);
            failTransaction(saved, "Deposit failed: " + e.getMessage());
            throw new TransactionValidationException("Deposit failed: " + e.getMessage());
        }
    }

    @Transactional
    public Page<Transaction> getMyTransactions(String keycloakUserId, TransactionStatus status, TransactionType type, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate, String search, Pageable pageable) {
        UUID myAccountId = null;
        try {
            BalanceOperationResponse account = accountServiceClient.getCheckingAccount(UUID.fromString(keycloakUserId));
            myAccountId = account.accountId();
        } catch (Exception e) {
            log.warn("Could not fetch checking account for user {}", keycloakUserId);
        }
        return transactionRepository.findByKeycloakUserIdOrAccountIdAndFilters(keycloakUserId, myAccountId, status, type, startDate, endDate, search, pageable);
    }

    @Transactional
    public Page<Transaction> getAllTransactions(TransactionStatus status, TransactionType type, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate, String search, Pageable pageable) {
        return transactionRepository.findAllByFilters(status, type, startDate, endDate, search, pageable);
    }

    @Transactional
    public AdminTransactionStatsResponse getAdminStats() {
        java.time.LocalDateTime todayStart = java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        java.math.BigDecimal volume = transactionRepository.sumVolumeSince(todayStart);
        long count = transactionRepository.countTransactionsSince(todayStart);
        long failedCount = transactionRepository.countFailedTransactionsSince(todayStart);

        return new AdminTransactionStatsResponse(volume, count, failedCount);
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
