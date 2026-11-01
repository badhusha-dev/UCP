package com.ecommerce.order.accounting.repository;

import com.ecommerce.order.accounting.model.AccountingAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountingAuditLogRepository extends JpaRepository<AccountingAuditLog, Long>, JpaSpecificationExecutor<AccountingAuditLog> {

    List<AccountingAuditLog> findByTransactionIdOrderByCreatedAtDesc(Long transactionId);

    List<AccountingAuditLog> findByAdminIdOrderByCreatedAtDesc(Long adminId);
}
