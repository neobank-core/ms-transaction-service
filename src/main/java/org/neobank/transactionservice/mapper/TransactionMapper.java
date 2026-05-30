package org.neobank.transactionservice.mapper;

import org.neobank.transactionservice.dto.TransactionResponse;
import org.neobank.transactionservice.entity.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus().name(),
                transaction.getType().name(),
                transaction.getCreatedAt()
        );
    }
}