package org.neobank.transactionservice.publisher;

import lombok.RequiredArgsConstructor;
import org.neobank.transactionservice.event.TransactionCompletedEvent;
import org.neobank.transactionservice.event.TransactionFailedEvent;
import org.neobank.transactionservice.event.TransactionInitiatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishInitiated(TransactionInitiatedEvent event) {
        kafkaTemplate.send("transaction.initiated", event.transactionId().toString(), event);
    }

    public void publishCompleted(TransactionCompletedEvent event) {
        kafkaTemplate.send("transaction.completed", event.transactionId().toString(), event);
    }

    public void publishFailed(TransactionFailedEvent event) {
        kafkaTemplate.send("transaction.failed", event.transactionId().toString(), event);
    }
}