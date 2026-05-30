package org.neobank.transactionservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neobank.transactionservice.entity.Transaction;
import org.neobank.transactionservice.enums.TransactionStatus;
import org.neobank.transactionservice.event.CardBlockedEvent;
import org.neobank.transactionservice.repository.TransactionRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardBlockedConsumer {

    private final TransactionRepository transactionRepository;

    @KafkaListener(topics = "card.blocked", groupId = "transaction-service-group")
    public void onCardBlocked(CardBlockedEvent event) {
        List<Transaction> pending = transactionRepository
                .findBySenderCardIdAndStatus(event.cardId(), TransactionStatus.PENDING);
        pending.forEach(tx -> tx.setStatus(TransactionStatus.FAILED));
        transactionRepository.saveAll(pending);
        log.info("Failed {} pending transactions for blocked card {}", pending.size(), event.cardId());
    }
}