package org.neobank.transactionservice.service;

import lombok.RequiredArgsConstructor;
import org.neobank.transactionservice.entity.Transaction;
import org.neobank.transactionservice.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionPersistenceService {

    private final TransactionRepository repository;

    @Transactional
    public Transaction save(Transaction transaction) {
        return repository.saveAndFlush(transaction);
    }
}