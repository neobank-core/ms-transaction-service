package org.neobank.transactionservice.service;

import lombok.RequiredArgsConstructor;
import org.neobank.transactionservice.entity.Transaction;
import org.neobank.transactionservice.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionLookupService {

    private final TransactionRepository repository;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public Transaction findByReferenceId(String idempotencyKey) {
        return repository.findByReferenceId(idempotencyKey)
                .orElseThrow();
    }
}