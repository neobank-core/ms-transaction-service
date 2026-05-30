package org.neobank.transactionservice.publisher;

import lombok.RequiredArgsConstructor;
import org.neobank.transactionservice.event.TransactionInitiatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(TransactionInitiatedEvent event) {
        kafkaTemplate.send("transaction.initiated",
                event.transactionId().toString(),
                event
        );
    }
}

