package com.ecommerce.order.accounting.repository;

import com.ecommerce.order.accounting.model.EntryType;
import com.ecommerce.order.accounting.model.LedgerTransaction;
import com.ecommerce.order.accounting.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long>, JpaSpecificationExecutor<LedgerTransaction> {

    Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    boolean existsByReversalOfId(Long reversalOfId);

    @Query("SELECT COALESCE(SUM(lt.amount), 0) FROM LedgerTransaction lt " +
           "WHERE lt.userId = :userId AND lt.entryType = :entryType AND lt.status = :status")
    BigDecimal sumAmountByUserIdAndEntryTypeAndStatus(
            @Param("userId") Long userId,
            @Param("entryType") EntryType entryType,
            @Param("status") TransactionStatus status
    );

    @Query("SELECT COALESCE(SUM(lt.amount), 0) FROM LedgerTransaction lt " +
           "WHERE lt.userId = :userId AND lt.transactionType = :transactionType AND lt.status = :status")
    BigDecimal sumAmountByUserIdAndTypeAndStatus(
            @Param("userId") Long userId,
            @Param("transactionType") com.ecommerce.order.accounting.model.TransactionType transactionType,
            @Param("status") TransactionStatus status
    );

    @Query("SELECT COALESCE(SUM(lt.amount), 0) FROM LedgerTransaction lt " +
           "WHERE lt.transactionType = :transactionType AND lt.status = :status")
    BigDecimal sumPlatformAmountByTypeAndStatus(
            @Param("transactionType") com.ecommerce.order.accounting.model.TransactionType transactionType,
            @Param("status") TransactionStatus status
    );

    @Query("SELECT COALESCE(SUM(lt.amount), 0) FROM LedgerTransaction lt " +
           "WHERE lt.entryType = :entryType AND lt.status = :status")
    BigDecimal sumPlatformAmountByEntryTypeAndStatus(
            @Param("entryType") EntryType entryType,
            @Param("status") TransactionStatus status
    );

    long countByReconciliationStatus(com.ecommerce.order.accounting.model.ReconciliationStatus reconciliationStatus);

    long countByStatus(TransactionStatus status);
}
