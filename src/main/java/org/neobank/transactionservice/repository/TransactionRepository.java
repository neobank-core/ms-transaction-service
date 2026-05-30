package org.neobank.transactionservice.repository;

import org.neobank.transactionservice.entity.Transaction;
import org.neobank.transactionservice.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByReferenceId(String referenceId);
    Page<Transaction> findByKeycloakUserId(String keycloakUserId, Pageable pageable);
    Optional<Transaction> findByIdAndKeycloakUserId(UUID id, String keycloakUserId);
    List<Transaction> findBySenderCardIdAndStatus(UUID senderCardId, TransactionStatus status);
}
