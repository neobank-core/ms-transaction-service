package org.neobank.transactionservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neobank.transactionservice.entity.Transaction;
import org.neobank.transactionservice.entity.TransactionLedger;
import org.neobank.transactionservice.enums.EntryType;
import org.neobank.transactionservice.enums.TransactionStatus;
import org.neobank.transactionservice.event.*;
import org.neobank.transactionservice.publisher.TransactionEventPublisher;
import org.neobank.transactionservice.repository.TransactionLedgerRepository;
import org.neobank.transactionservice.repository.TransactionRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionSagaListener {

    private final TransactionRepository transactionRepository;
    private final TransactionLedgerRepository ledgerRepository;
    private final TransactionEventPublisher publisher;

    @KafkaListener(topics = "account.debited", groupId = "transaction-saga-group")
    @Transactional
    public void handleSenderDebited(SenderDebitedEvent event) {
        log.info("Saga update: Sender debited for tx {}", event.transactionId());
        Transaction tx = transactionRepository.findById(event.transactionId()).orElse(null);
        if (tx != null) {
            ledgerRepository.save(TransactionLedger.builder()
                    .transaction(tx)
                    .entryType(EntryType.DEBIT)
                    .accountId(event.senderAccountId())
                    .amount(event.amount().negate())
                    .balanceAfter(event.senderBalanceAfter())
                    .build());
        }
    }

    @KafkaListener(topics = "account.credited", groupId = "transaction-saga-group")
    @Transactional
    public void handleReceiverCredited(ReceiverCreditedEvent event) {
        log.info("Saga update: Receiver credited for tx {}. Saga completed successfully.", event.transactionId());
        Transaction tx = transactionRepository.findById(event.transactionId()).orElse(null);
        if (tx != null) {
            tx.setStatus(TransactionStatus.COMPLETED);
            transactionRepository.save(tx);

            ledgerRepository.save(TransactionLedger.builder()
                    .transaction(tx)
                    .entryType(EntryType.CREDIT)
                    .accountId(event.receiverAccountId())
                    .amount(event.amount())
                    .balanceAfter(event.receiverBalanceAfter())
                    .build());
            
            publisher.publishCompleted(new TransactionCompletedEvent(
                    tx.getId(),
                    tx.getKeycloakUserId(),
                    tx.getSenderCardId(),
                    tx.getReceiverCardId(),
                    tx.getAmount(),
                    tx.getCurrency(),
                    LocalDateTime.now()
            ));
        }
    }

    @KafkaListener(topics = "account.credit.failed", groupId = "transaction-saga-group")
    @Transactional
    public void handleReceiverCreditFailed(ReceiverCreditFailedEvent event) {
        log.error("Saga update: Receiver credit failed for tx {}. Initiating compensation.", event.transactionId());
        Transaction tx = transactionRepository.findById(event.transactionId()).orElse(null);
        if (tx != null) {
            tx.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(tx);

            publisher.publishCompensate(new CompensateSenderDebitEvent(
                    event.transactionId(),
                    event.senderAccountId(),
                    event.amount(),
                    event.currency()
            ));

            publisher.publishFailed(new TransactionFailedEvent(
                    tx.getId(),
                    tx.getKeycloakUserId(),
                    "Receiver credit failed: " + event.reason(),
                    LocalDateTime.now()
            ));
        }
    }

    @KafkaListener(topics = "transaction.failed", groupId = "transaction-saga-group")
    @Transactional
    public void handleTransactionFailed(TransactionFailedEvent event) {
        log.error("Saga update: Transaction failed for tx {}", event.transactionId());
        Transaction tx = transactionRepository.findById(event.transactionId()).orElse(null);
        if (tx != null && tx.getStatus() == TransactionStatus.PENDING) {
            tx.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(tx);
        }
    }
}
