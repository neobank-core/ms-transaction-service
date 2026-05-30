package org.neobank.transactionservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neobank.transactionservice.event.CardBlockedEvent;
import org.neobank.transactionservice.service.TransactionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardBlockedConsumer {

    private final TransactionService transactionService;

    @KafkaListener(topics = "card.blocked", groupId = "transaction-service-group")
    public void onCardBlocked(CardBlockedEvent event) {
        transactionService.failPendingTransactionsForCard(event.cardId(), "Card blocked");
        log.info("Processed card.blocked for card {}", event.cardId());
    }
}