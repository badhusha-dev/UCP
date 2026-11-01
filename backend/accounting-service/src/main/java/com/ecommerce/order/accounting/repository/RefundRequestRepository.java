package com.ecommerce.order.accounting.repository;

import com.ecommerce.order.accounting.model.RefundRequest;
import com.ecommerce.order.accounting.model.RefundRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long>, JpaSpecificationExecutor<RefundRequest> {

    Page<RefundRequest> findByUserId(Long userId, Pageable pageable);

    Page<RefundRequest> findByUserIdAndStatus(Long userId, RefundRequestStatus status, Pageable pageable);

    List<RefundRequest> findByOrderId(Long orderId);

    long countByUserIdAndStatus(Long userId, RefundRequestStatus status);

    long countByStatus(RefundRequestStatus status);
}
