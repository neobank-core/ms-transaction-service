package org.neobank.transactionservice.repository;

import org.neobank.transactionservice.entity.TransactionLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionLedgerRepository extends JpaRepository<TransactionLedger, UUID> {
    List<TransactionLedger> findByTransactionId(UUID transactionId);
}
