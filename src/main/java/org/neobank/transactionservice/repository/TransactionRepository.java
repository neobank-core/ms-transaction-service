package org.neobank.transactionservice.repository;

import org.neobank.transactionservice.entity.Transaction;
import org.neobank.transactionservice.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByReferenceId(String referenceId);
    @Query("SELECT t FROM Transaction t WHERE " +
           "(:status IS NULL OR t.status = :status) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (cast(:startDate as timestamp) IS NULL OR t.createdAt >= :startDate) " +
           "AND (cast(:endDate as timestamp) IS NULL OR t.createdAt <= :endDate) " +
           "AND (:search IS NULL OR CAST(t.id AS string) LIKE %:search% OR t.referenceId LIKE %:search%)")
    Page<Transaction> findAllByFilters(
            @Param("status") TransactionStatus status, 
            @Param("type") org.neobank.transactionservice.enums.TransactionType type, 
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.keycloakUserId = :userId " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (cast(:startDate as timestamp) IS NULL OR t.createdAt >= :startDate) " +
           "AND (cast(:endDate as timestamp) IS NULL OR t.createdAt <= :endDate) " +
           "AND (:search IS NULL OR CAST(t.id AS string) LIKE %:search% OR t.referenceId LIKE %:search%)")
    Page<Transaction> findByKeycloakUserIdAndFilters(
            @Param("userId") String keycloakUserId, 
            @Param("status") TransactionStatus status, 
            @Param("type") org.neobank.transactionservice.enums.TransactionType type, 
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN TransactionLedger l ON t.id = l.transaction.id " +
           "WHERE (t.keycloakUserId = :userId OR l.accountId = :accountId) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (cast(:startDate as timestamp) IS NULL OR t.createdAt >= :startDate) " +
           "AND (cast(:endDate as timestamp) IS NULL OR t.createdAt <= :endDate) " +
           "AND (:search IS NULL OR CAST(t.id AS string) LIKE %:search% OR t.referenceId LIKE %:search%)")
    Page<Transaction> findByKeycloakUserIdOrAccountIdAndFilters(
            @Param("userId") String keycloakUserId, 
            @Param("accountId") UUID accountId,
            @Param("status") TransactionStatus status, 
            @Param("type") org.neobank.transactionservice.enums.TransactionType type, 
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            @Param("search") String search,
            Pageable pageable);
    
    Page<Transaction> findByKeycloakUserId(String keycloakUserId, Pageable pageable);
    Optional<Transaction> findByIdAndKeycloakUserId(UUID id, String keycloakUserId);
    List<Transaction> findBySenderCardIdAndStatus(UUID senderCardId, TransactionStatus status);
    @Query("SELECT DISTINCT t FROM Transaction t JOIN TransactionLedger l ON t.id = l.transaction.id WHERE l.accountId = :accountId")
    List<Transaction> findAllByAccountId(@Param("accountId") UUID accountId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.keycloakUserId = :userId " +
           "AND t.type = 'TRANSFER' " +
           "AND t.status IN ('COMPLETED', 'PENDING') " +
           "AND t.createdAt >= :startDate")
    java.math.BigDecimal getDailyTransferSum(@Param("userId") String userId, @Param("startDate") java.time.LocalDateTime startDate);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.createdAt >= :startDate AND t.status IN ('COMPLETED', 'PENDING')")
    java.math.BigDecimal sumVolumeSince(@Param("startDate") java.time.LocalDateTime startDate);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.createdAt >= :startDate")
    long countTransactionsSince(@Param("startDate") java.time.LocalDateTime startDate);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.status = 'FAILED' AND t.createdAt >= :startDate")
    long countFailedTransactionsSince(@Param("startDate") java.time.LocalDateTime startDate);
}
