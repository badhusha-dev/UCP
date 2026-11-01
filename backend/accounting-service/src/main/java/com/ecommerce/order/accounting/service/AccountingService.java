package com.ecommerce.order.accounting.service;

import com.ecommerce.order.accounting.dto.*;
import com.ecommerce.order.accounting.model.*;
import com.ecommerce.order.accounting.repository.*;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.OrderDataSyncService;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.criteria.Predicate;
import com.ecommerce.order.accounting.security.AccountingTenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingService {

    private final LedgerTransactionRepository ledgerRepository;
    private final AccountingAuditLogRepository auditLogRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final CustomerAccountingPreferencesRepository preferencesRepository;
    private final CustomerPaymentMethodRepository paymentMethodRepository;
    private final OrderRepository orderRepository;
    private final OrderDataSyncService orderDataSyncService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Records an idempotent order payment transaction when an order is created.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerTransaction recordOrderPayment(Order order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Order and Order ID must not be null");
        }
        String idempotencyKey = "ORDER_PAYMENT_" + order.getId();

        Optional<LedgerTransaction> existing = ledgerRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent check hit: Payment transaction already recorded for Order #{}", order.getId());
            return existing.get();
        }

        BigDecimal amount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Order #{} has zero or negative amount, skipping ledger payment entry.", order.getId());
            return null;
        }

        Long targetUserId = (order.getUserId() != null && order.getUserId() > 0) ? order.getUserId() : 1L;

        LedgerTransaction transaction = LedgerTransaction.builder()
                .userId(targetUserId)
                .transactionType(TransactionType.ORDER_PAYMENT)
                .entryType(EntryType.DEBIT)
                .amount(amount.setScale(4, RoundingMode.HALF_UP))
                .currency("USD")
                .status(TransactionStatus.POSTED)
                .description("Payment for Order #" + order.getId())
                .referenceType("ORDER")
                .referenceId(String.valueOf(order.getId()))
                .idempotencyKey(idempotencyKey)
                .createdBy("SYSTEM")
                .reconciliationStatus(ReconciliationStatus.RECONCILED)
                .paymentProvider("SIMULATED_GATEWAY")
                .providerReference("CH_SIM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .createdAt(LocalDateTime.now())
                .build();

        LedgerTransaction saved = ledgerRepository.save(transaction);
        log.info("Recorded immutable ledger payment transaction #{} for Order #{}", saved.getId(), order.getId());
        return saved;
    }

    /**
     * Records an idempotent refund transaction when an order is cancelled or refunded.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerTransaction recordOrderRefund(Order order, String reason) {
        return recordOrderRefund(order, reason, "ORDER_REFUND_" + (order != null ? order.getId() : "null"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerTransaction recordOrderRefund(Order order, String reason, String idempotencyKey) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Order and Order ID must not be null");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Refund idempotency key must not be blank");
        }

        Optional<LedgerTransaction> existing = ledgerRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent check hit: Refund transaction already recorded for Order #{}", order.getId());
            return existing.get();
        }

        BigDecimal amount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        Long targetUserId = (order.getUserId() != null && order.getUserId() > 0) ? order.getUserId() : 1L;

        LedgerTransaction transaction = LedgerTransaction.builder()
                .userId(targetUserId)
                .transactionType(TransactionType.ORDER_REFUND)
                .entryType(EntryType.CREDIT)
                .amount(amount.setScale(4, RoundingMode.HALF_UP))
                .currency("USD")
                .status(TransactionStatus.POSTED)
                .description("Refund for Cancelled Order #" + order.getId())
                .referenceType("ORDER")
                .referenceId(String.valueOf(order.getId()))
                .idempotencyKey(idempotencyKey)
                .reason(reason != null ? reason : "Order cancellation refund")
                .createdBy("SYSTEM")
                .reconciliationStatus(ReconciliationStatus.RECONCILED)
                .paymentProvider("SIMULATED_GATEWAY")
                .providerReference("REF_SIM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .createdAt(LocalDateTime.now())
                .build();

        LedgerTransaction saved = ledgerRepository.save(transaction);
        log.info("Recorded immutable ledger refund transaction #{} for Order #{}", saved.getId(), order.getId());
        return saved;
    }

    /**
     * Customer query: retrieves net account balance with calculation note.
     */
    @Transactional(readOnly = true)
    public AccountBalanceDto getAccountBalance(Long userId) {
        BigDecimal totalCredits = ledgerRepository.sumAmountByUserIdAndEntryTypeAndStatus(
                userId, EntryType.CREDIT, TransactionStatus.POSTED);
        BigDecimal totalDebits = ledgerRepository.sumAmountByUserIdAndEntryTypeAndStatus(
                userId, EntryType.DEBIT, TransactionStatus.POSTED);

        BigDecimal netBalance = totalCredits.subtract(totalDebits).setScale(2, RoundingMode.HALF_UP);

        return AccountBalanceDto.builder()
                .userId(userId)
                .currency("USD")
                .totalCredits(totalCredits.setScale(2, RoundingMode.HALF_UP))
                .totalDebits(totalDebits.setScale(2, RoundingMode.HALF_UP))
                .netBalance(netBalance)
                .explanation("Net Balance is calculated as (Total Credits + Refunds) minus (Total Payments + Debits). A positive balance indicates available store credit or refund owed to you. All ledger entries are immutable and preserved for financial auditability.")
                .build();
    }

    /**
     * Customer query: comprehensive Account Overview with lifetime metrics and pending transactions.
     */
    @Transactional(readOnly = true)
    public AccountOverviewDto getAccountOverview(Long userId) {
        BigDecimal totalCredits = ledgerRepository.sumAmountByUserIdAndEntryTypeAndStatus(
                userId, EntryType.CREDIT, TransactionStatus.POSTED);
        BigDecimal totalDebits = ledgerRepository.sumAmountByUserIdAndEntryTypeAndStatus(
                userId, EntryType.DEBIT, TransactionStatus.POSTED);

        BigDecimal lifetimeSpend = ledgerRepository.sumAmountByUserIdAndTypeAndStatus(
                userId, TransactionType.ORDER_PAYMENT, TransactionStatus.POSTED);
        BigDecimal lifetimeRefunds = ledgerRepository.sumAmountByUserIdAndTypeAndStatus(
                userId, TransactionType.ORDER_REFUND, TransactionStatus.POSTED);

        BigDecimal netActivity = totalCredits.subtract(totalDebits).setScale(2, RoundingMode.HALF_UP);
        BigDecimal storeCreditIssued = ledgerRepository.sumAmountByUserIdAndTypeAndStatus(
                userId, TransactionType.MANUAL_CREDIT, TransactionStatus.POSTED);
        BigDecimal availableStoreCredit = storeCreditIssued != null ? storeCreditIssued.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        long pendingRefunds = refundRequestRepository.countByUserIdAndStatus(userId, RefundRequestStatus.REQUESTED);

        return AccountOverviewDto.builder()
                .userId(userId)
                .currency("USD")
                .availableStoreCredit(availableStoreCredit.setScale(2, RoundingMode.HALF_UP))
                .lifetimeSpend(lifetimeSpend.setScale(2, RoundingMode.HALF_UP))
                .lifetimeRefunds(lifetimeRefunds.setScale(2, RoundingMode.HALF_UP))
                .totalCredits(totalCredits.setScale(2, RoundingMode.HALF_UP))
                .totalDebits(totalDebits.setScale(2, RoundingMode.HALF_UP))
                .netActivity(netActivity)
                .pendingTransactionsCount(pendingRefunds)
                .pendingTransactionsAmount(BigDecimal.ZERO)
                .storeCreditExplanation("Available store balance accrued from customer credits and refund adjustments, redeemable during checkout.")
                .lifetimeSpendExplanation("Sum of all settled, successful order purchase payments posted to your account.")
                .lifetimeRefundsExplanation("Total monetary value credited back to your account across all historical order cancellations and returns.")
                .netActivityExplanation("Calculated as Total Settled Credits minus Total Settled Debits across all historical records.")
                .pendingExplanation("Count of submitted refund requests or pending orders currently undergoing review.")
                .build();
    }

    /**
     * Customer query: retrieves paginated transactions for a specific customer.
     */
    @Transactional(readOnly = true)
    public Page<LedgerTransactionDto> getCustomerLedger(
            Long userId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            TransactionType type,
            TransactionStatus status,
            Pageable pageable) {

        Specification<LedgerTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("transactionType"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ledgerRepository.findAll(spec, pageable).map(this::toDto);
    }

    /**
     * Admin query: cross-customer search, filtering, and pagination.
     */
    @Transactional(readOnly = true)
    public Page<LedgerTransactionDto> getAdminLedger(
            Long userId,
            String customerEmail,
            LocalDateTime startDate,
            LocalDateTime endDate,
            TransactionType type,
            TransactionStatus status,
            String referenceId,
            ReconciliationStatus reconciliationStatus,
            Pageable pageable) {

        Specification<LedgerTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (customerEmail != null && !customerEmail.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("customerEmail")), "%" + customerEmail.toLowerCase().trim() + "%"));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("transactionType"), type));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (referenceId != null && !referenceId.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("referenceId")), "%" + referenceId.toLowerCase() + "%"));
            }
            if (reconciliationStatus != null) {
                predicates.add(cb.equal(root.get("reconciliationStatus"), reconciliationStatus));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ledgerRepository.findAll(spec, pageable).map(this::toDto);
    }

    /**
     * Retrieves single transaction detail by ID.
     */
    // @TenantId scopes queries and inserts, but Hibernate does not apply it to a load by primary key, so by-id lookups
    // check the tenant explicitly (another tenant's row reads as "not found").
    private java.util.Optional<LedgerTransaction> ledgerInTenant(Long id) {
        return ledgerRepository.findById(id).filter(tx -> AccountingTenantContext.current().equals(tx.getTenantId()));
    }

    private java.util.Optional<RefundRequest> refundInTenant(Long id) {
        return refundRequestRepository.findById(id).filter(refund -> AccountingTenantContext.current().equals(refund.getTenantId()));
    }

    @Transactional(readOnly = true)
    public LedgerTransactionDto getTransactionById(Long id) {
        return ledgerInTenant(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger transaction not found with ID: " + id));
    }

    /**
     * Records an auditable manual adjustment (credit, debit, refund) with mandatory reason and reference.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerTransactionDto createManualAdjustment(
            Long adminId,
            String adminEmail,
            ManualAdjustmentRequest request) {

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Reason is required for manual adjustments.");
        }
        if (request.getReference() == null || request.getReference().trim().isEmpty()) {
            throw new IllegalArgumentException("Reference is required for manual adjustments.");
        }

        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = "MANUAL_ADJ_" + UUID.randomUUID();
        }

        if (ledgerRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new IllegalStateException("Duplicate transaction: An entry with idempotency key " + idempotencyKey + " already exists.");
        }

        EntryType entryType;
        if (request.getTransactionType() == TransactionType.MANUAL_CREDIT ||
            request.getTransactionType() == TransactionType.ORDER_REFUND ||
            request.getTransactionType() == TransactionType.STORE_CREDIT_TOPUP) {
            entryType = EntryType.CREDIT;
        } else if (request.getTransactionType() == TransactionType.MANUAL_DEBIT ||
                   request.getTransactionType() == TransactionType.ORDER_PAYMENT) {
            entryType = EntryType.DEBIT;
        } else {
            entryType = EntryType.CREDIT;
        }

        LedgerTransaction transaction = LedgerTransaction.builder()
                .userId(request.getUserId())
                .transactionType(request.getTransactionType())
                .entryType(entryType)
                .amount(request.getAmount().setScale(4, RoundingMode.HALF_UP))
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .status(TransactionStatus.POSTED)
                .description("Manual adjustment: " + request.getTransactionType() + " (Ref: " + request.getReference() + ")")
                .referenceType(request.getReferenceType())
                .referenceId(request.getReference())
                .idempotencyKey(idempotencyKey)
                .reason(request.getReason())
                .createdBy(adminEmail != null ? adminEmail : "ADMIN#" + adminId)
                .reconciliationStatus(ReconciliationStatus.RECONCILED)
                .paymentProvider("MANUAL_ADMIN")
                .providerReference(request.getReference())
                .createdAt(LocalDateTime.now())
                .build();

        LedgerTransaction saved = ledgerRepository.save(transaction);

        recordAudit(
                saved.getId(),
                adminId,
                adminEmail,
                "MANUAL_ADJUSTMENT_" + request.getTransactionType(),
                request.getReason(),
                null,
                toJson(saved)
        );

        log.info("Admin {} created manual adjustment transaction #{} for User #{}", adminEmail, saved.getId(), request.getUserId());
        return toDto(saved);
    }

    /**
     * Non-destructive transaction reversal with compensating entry and mandatory audit log.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerTransactionDto reverseTransaction(
            Long adminId,
            String adminEmail,
            Long transactionId,
            ReversalRequest request) {

        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Reversal reason is mandatory for financial auditability.");
        }

        LedgerTransaction original = ledgerInTenant(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction #" + transactionId + " not found."));

        if (original.getTransactionType() == TransactionType.REVERSAL) {
            throw new IllegalStateException("Cannot reverse a reversal transaction.");
        }

        if (original.getStatus() == TransactionStatus.REVERSED || ledgerRepository.existsByReversalOfId(transactionId)) {
            throw new IllegalStateException("Transaction #" + transactionId + " has already been reversed.");
        }

        String beforeStateJson = toJson(original);

        EntryType compensatingEntryType = (original.getEntryType() == EntryType.CREDIT) ? EntryType.DEBIT : EntryType.CREDIT;

        String reversalKey = "REVERSAL_" + original.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);

        LedgerTransaction compensatingEntry = LedgerTransaction.builder()
                .userId(original.getUserId())
                .transactionType(TransactionType.REVERSAL)
                .entryType(compensatingEntryType)
                .amount(original.getAmount())
                .currency(original.getCurrency())
                .status(TransactionStatus.POSTED)
                .description("Reversal of Transaction #" + original.getId() + " (" + original.getDescription() + ")")
                .referenceType(original.getReferenceType())
                .referenceId(original.getReferenceId())
                .idempotencyKey(reversalKey)
                .reversalOfId(original.getId())
                .reason(request.getReason())
                .createdBy(adminEmail != null ? adminEmail : "ADMIN#" + adminId)
                .reconciliationStatus(ReconciliationStatus.RECONCILED)
                .paymentProvider("REVERSAL_COMPENSATION")
                .providerReference("REV_" + original.getId())
                .createdAt(LocalDateTime.now())
                .build();

        LedgerTransaction savedReversal = ledgerRepository.save(compensatingEntry);

        recordAudit(
                savedReversal.getId(),
                adminId,
                adminEmail,
                "TRANSACTION_REVERSAL",
                request.getReason(),
                beforeStateJson,
                toJson(savedReversal)
        );

        log.info("Admin {} created reversing transaction #{} for original transaction #{}",
                adminEmail, savedReversal.getId(), original.getId());

        return toDto(savedReversal);
    }

    /**
     * Generates printable Order Invoice and Receipt DTO.
     */
    @Transactional(readOnly = true)
    public OrderInvoiceDto getOrderInvoice(Long orderId, Long requesterUserId, String requesterRole) {
        Order order = orderDataSyncService.refreshOrder(orderId);
        if (order == null) order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order #" + orderId + " not found."));

        boolean isAdminOrManager = requesterRole != null &&
                (requesterRole.contains("ADMIN") || requesterRole.contains("MANAGER"));

        if (!order.getUserId().equals(requesterUserId) && !isAdminOrManager) {
            log.warn("Security Alert: User #{} attempted unauthorized invoice retrieval for Order #{} owned by User #{}",
                    requesterUserId, orderId, order.getUserId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not authorized to view this order invoice.");
        }

        List<InvoiceLineItemDto> lineItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                BigDecimal itemPrice = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                int qty = item.getQuantity() != null ? item.getQuantity() : 1;
                BigDecimal lineTotal = itemPrice.multiply(BigDecimal.valueOf(qty));
                subtotal = subtotal.add(lineTotal);

                lineItems.add(InvoiceLineItemDto.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName() != null ? item.getProductName() : "Product #" + item.getProductId())
                        .quantity(qty)
                        .unitPrice(itemPrice.setScale(2, RoundingMode.HALF_UP))
                        .lineTotal(lineTotal.setScale(2, RoundingMode.HALF_UP))
                        .build());
            }
        }

        String paymentStatus = "CANCELLED".equalsIgnoreCase(order.getStatus()) || "REFUNDED".equalsIgnoreCase(order.getStatus())
                ? "REFUNDED"
                : "PAID";

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd");
        String invDateStr = order.getCreatedAt() != null ? order.getCreatedAt().format(dtf) : "20261022";
        String invoiceNumber = "INV-" + order.getId() + "-" + invDateStr;

        return OrderInvoiceDto.builder()
                .invoiceNumber(invoiceNumber)
                .orderId(order.getId())
                .orderDate(order.getCreatedAt())
                .userId(order.getUserId())
                .customerEmail("Customer #" + order.getUserId())
                .billingAddress("Customer Verified Address On File")
                .items(lineItems)
                .subtotal(subtotal.setScale(2, RoundingMode.HALF_UP))
                .discountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .shippingFee(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .taxAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .totalAmount(order.getTotalAmount() != null ? order.getTotalAmount().setScale(2, RoundingMode.HALF_UP) : subtotal)
                .currency("USD")
                .orderStatus(order.getStatus())
                .paymentStatus(paymentStatus)
                .paymentMethodLabel("Simulated Merchant Gateway - Tokenized Settlement")
                .paymentReference("PAY_REF_" + order.getId())
                .disclaimer("This receipt is generated from internal platform order records for customer transaction verification. Taxes and shipping are itemized when applicable.")
                .build();
    }

    /**
     * Customer submits a refund request for an existing order.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RefundRequestDto submitRefundRequest(Long userId, String userEmail, CreateRefundRequest request) {
        Order order = orderDataSyncService.refreshOrder(request.getOrderId());
        if (order == null) order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order #" + request.getOrderId() + " not found."));

        if (!order.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only request refunds for your own orders.");
        }

        BigDecimal refundAmount = request.getAmount() != null ? request.getAmount() : order.getTotalAmount();
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero.");
        }

        if (refundAmount.compareTo(order.getTotalAmount()) > 0) {
            throw new IllegalArgumentException("Refund amount cannot exceed order total of $" + order.getTotalAmount());
        }

        RefundRequest entity = RefundRequest.builder()
                .order(order)
                .userId(userId)
                .customerEmail(userEmail)
                .amount(refundAmount.setScale(4, RoundingMode.HALF_UP))
                .currency("USD")
                .reason(request.getReason())
                .status(RefundRequestStatus.REQUESTED)
                .requestedBy(userEmail != null ? userEmail : "USER#" + userId)
                .build();

        RefundRequest saved = refundRequestRepository.save(entity);
        log.info("Customer {} submitted refund request #{} for Order #{}", userEmail, saved.getId(), order.getId());

        return toRefundDto(saved);
    }

    /**
     * Administrator processes (approves or rejects) a refund request.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RefundRequestDto processRefundRequest(Long adminId, String adminEmail, Long refundRequestId, ProcessRefundRequest request) {
        RefundRequest refund = refundInTenant(refundRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund request #" + refundRequestId + " not found."));

        if (refund.getStatus() != RefundRequestStatus.REQUESTED) {
            throw new IllegalStateException("Refund request #" + refundRequestId + " is already in " + refund.getStatus() + " status.");
        }

        String beforeState = toJson(refund);

        if (Boolean.TRUE.equals(request.getApprove())) {
            refund.setStatus(RefundRequestStatus.APPROVED);
            refund.setApprovedBy(adminEmail != null ? adminEmail : "ADMIN#" + adminId);
            refund.setDecisionReason(request.getDecisionReason() != null ? request.getDecisionReason() : "Approved by administrator");

            // Post immutable refund entry to ledger
            Order order = orderDataSyncService.refreshOrder(refund.getOrder().getId());
            if (order == null) order = refund.getOrder();
            LedgerTransaction refundTx = recordOrderRefund(order, "Approved Refund Request #" + refund.getId() + ": " + refund.getReason());
            if (refundTx != null) {
                refund.setLedgerTransactionId(refundTx.getId());
            }

            // Update order status to REFUNDED
            orderDataSyncService.updateOrderStatus(order.getId(), "REFUNDED");

            refund.setStatus(RefundRequestStatus.PROCESSED);

            recordAudit(
                    refundTx != null ? refundTx.getId() : null,
                    adminId,
                    adminEmail,
                    "REFUND_APPROVED",
                    refund.getDecisionReason(),
                    beforeState,
                    toJson(refund)
            );
            log.info("Admin {} approved and processed refund request #{} for Order #{}", adminEmail, refund.getId(), refund.getOrder().getId());
        } else {
            refund.setStatus(RefundRequestStatus.REJECTED);
            refund.setApprovedBy(adminEmail != null ? adminEmail : "ADMIN#" + adminId);
            refund.setDecisionReason(request.getDecisionReason() != null ? request.getDecisionReason() : "Refund request declined by administrator");

            recordAudit(
                    null,
                    adminId,
                    adminEmail,
                    "REFUND_REJECTED",
                    refund.getDecisionReason(),
                    beforeState,
                    toJson(refund)
            );
            log.info("Admin {} rejected refund request #{} for Order #{}", adminEmail, refund.getId(), refund.getOrder().getId());
        }

        RefundRequest updated = refundRequestRepository.save(refund);
        return toRefundDto(updated);
    }

    /**
     * Customer queries their own refund requests.
     */
    @Transactional(readOnly = true)
    public Page<RefundRequestDto> getRefundRequests(Long userId, RefundRequestStatus status, Pageable pageable) {
        if (status != null) {
            return refundRequestRepository.findByUserIdAndStatus(userId, status, pageable).map(this::toRefundDto);
        }
        return refundRequestRepository.findByUserId(userId, pageable).map(this::toRefundDto);
    }

    /**
     * Admin queries refund requests across customers.
     */
    @Transactional(readOnly = true)
    public Page<RefundRequestDto> getAdminRefundRequests(Long orderId, Long userId, RefundRequestStatus status, Pageable pageable) {
        Specification<RefundRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (orderId != null) {
                predicates.add(cb.equal(root.get("order").get("id"), orderId));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return refundRequestRepository.findAll(spec, pageable).map(this::toRefundDto);
    }

    /**
     * Admin Accounting Executive Dashboard Summary.
     */
    @Transactional(readOnly = true)
    public AccountingDashboardDto getAdminDashboard(LocalDateTime startDate, LocalDateTime endDate, String currency) {
        String curr = currency == null || currency.isBlank() ? "USD" : currency.toUpperCase(Locale.ROOT);

        BigDecimal grossSales = ledgerRepository.sumPlatformByTypeStatusAndWindow(
                TransactionType.ORDER_PAYMENT, TransactionStatus.POSTED, curr, startDate, endDate);
        BigDecimal totalRefunds = ledgerRepository.sumPlatformByTypeStatusAndWindow(
                TransactionType.ORDER_REFUND, TransactionStatus.POSTED, curr, startDate, endDate);

        BigDecimal totalCredits = ledgerRepository.sumPlatformByEntryStatusAndWindow(
                EntryType.CREDIT, TransactionStatus.POSTED, curr, startDate, endDate);
        BigDecimal totalDebits = ledgerRepository.sumPlatformByEntryStatusAndWindow(
                EntryType.DEBIT, TransactionStatus.POSTED, curr, startDate, endDate);

        BigDecimal totalManualAdjustments = ledgerRepository.sumPlatformByTypeStatusAndWindow(
                TransactionType.MANUAL_CREDIT, TransactionStatus.POSTED, curr, startDate, endDate)
                .add(ledgerRepository.sumPlatformByTypeStatusAndWindow(
                        TransactionType.MANUAL_DEBIT, TransactionStatus.POSTED, curr, startDate, endDate));

        BigDecimal netRecordedActivity = totalCredits.subtract(totalDebits).setScale(2, RoundingMode.HALF_UP);

        List<TransactionStatus> unsettledStatuses = List.of(TransactionStatus.FAILED, TransactionStatus.PENDING);
        long failedCount = ledgerRepository.countPlatformByStatusesAndWindow(unsettledStatuses, curr, startDate, endDate);
        BigDecimal unsettledAmount = ledgerRepository.sumPlatformByStatusesAndWindow(unsettledStatuses, curr, startDate, endDate);
        long flaggedReconciliation = ledgerRepository.countPlatformByReconciliationAndWindow(ReconciliationStatus.FLAGGED_DISCREPANCY, curr, startDate, endDate);
        long totalTransactions = ledgerRepository.countPlatformByWindow(curr, startDate, endDate);

        return AccountingDashboardDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .currency(curr)
                .grossSales(grossSales.setScale(2, RoundingMode.HALF_UP))
                .totalRefunds(totalRefunds.setScale(2, RoundingMode.HALF_UP))
                .totalCredits(totalCredits.setScale(2, RoundingMode.HALF_UP))
                .totalDebits(totalDebits.setScale(2, RoundingMode.HALF_UP))
                .totalAdjustments(totalManualAdjustments.setScale(2, RoundingMode.HALF_UP))
                .netRecordedActivity(netRecordedActivity)
                .failedOrPendingCount(failedCount)
                .failedOrPendingAmount(unsettledAmount.setScale(2, RoundingMode.HALF_UP))
                .flaggedReconciliationCount(flaggedReconciliation)
                .totalTransactionsCount(totalTransactions)
                .disclaimer("Notice: These figures represent internal platform transaction subledger totals recorded in order_db. They are not audited financial statements or GAAP/IFRS balance sheets.")
                .build();
    }

    /**
     * Updates reconciliation status on a specific transaction (Flagging, Reviewing, Resolving).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerTransactionDto updateReconciliationStatus(
            Long adminId,
            String adminEmail,
            Long transactionId,
            ReconciliationUpdateRequest request) {

        LedgerTransaction tx = ledgerInTenant(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction #" + transactionId + " not found."));

        String beforeJson = toJson(tx);

        tx.setReconciliationStatus(request.getStatus());
        if (request.getNotes() != null) {
            tx.setReconciliationNotes(request.getNotes());
        }

        LedgerTransaction saved = ledgerRepository.save(tx);

        recordAudit(
                tx.getId(),
                adminId,
                adminEmail,
                "RECONCILIATION_STATUS_UPDATE",
                "Reconciliation set to " + request.getStatus() + ": " + (request.getNotes() != null ? request.getNotes() : "No notes"),
                beforeJson,
                toJson(saved)
        );

        log.info("Admin {} updated reconciliation status on Tx #{} to {}", adminEmail, transactionId, request.getStatus());
        return toDto(saved);
    }

    /**
     * Customer preferences: fetch or initialize.
     */
    @Transactional
    public AccountingPreferencesDto getPreferences(Long userId) {
        CustomerAccountingPreferences pref = preferencesRepository.findById(userId)
                .orElseGet(() -> {
                    CustomerAccountingPreferences newPref = CustomerAccountingPreferences.builder()
                            .userId(userId)
                            .emailReceipts(true)
                            .statementNotifications(false)
                            .build();
                    return preferencesRepository.save(newPref);
                });

        return AccountingPreferencesDto.builder()
                .userId(pref.getUserId())
                .emailReceipts(pref.getEmailReceipts())
                .statementNotifications(pref.getStatementNotifications())
                .build();
    }

    /**
     * Customer preferences: update.
     */
    @Transactional
    public AccountingPreferencesDto updatePreferences(Long userId, AccountingPreferencesDto dto) {
        CustomerAccountingPreferences pref = preferencesRepository.findById(userId)
                .orElse(CustomerAccountingPreferences.builder().userId(userId).build());

        if (dto.getEmailReceipts() != null) pref.setEmailReceipts(dto.getEmailReceipts());
        if (dto.getStatementNotifications() != null) pref.setStatementNotifications(dto.getStatementNotifications());

        CustomerAccountingPreferences saved = preferencesRepository.save(pref);
        return AccountingPreferencesDto.builder()
                .userId(saved.getUserId())
                .emailReceipts(saved.getEmailReceipts())
                .statementNotifications(saved.getStatementNotifications())
                .build();
    }

    /**
     * Returns saved tokenized payment methods (Non-sensitive metadata only).
     */
    @Transactional
    public List<PaymentMethodDto> getPaymentMethods(Long userId) {
        List<CustomerPaymentMethod> methods = paymentMethodRepository.findByUserId(userId);
        if (methods.isEmpty()) {
            CustomerPaymentMethod defaultMethod = CustomerPaymentMethod.builder()
                    .userId(userId)
                    .provider("SIMULATED_GATEWAY")
                    .paymentToken("tok_sim_" + UUID.randomUUID().toString().substring(0, 12))
                    .cardBrand("Visa")
                    .last4("4242")
                    .expMonth(12)
                    .expYear(2028)
                    .isDefault(true)
                    .build();
            paymentMethodRepository.save(defaultMethod);
            methods = List.of(defaultMethod);
        }

        List<PaymentMethodDto> dtos = new ArrayList<>();
        for (CustomerPaymentMethod pm : methods) {
            dtos.add(PaymentMethodDto.builder()
                    .id(pm.getId())
                    .provider(pm.getProvider())
                    .cardBrand(pm.getCardBrand())
                    .last4(pm.getLast4())
                    .expMonth(pm.getExpMonth())
                    .expYear(pm.getExpYear())
                    .isDefault(pm.getIsDefault())
                    .label(pm.getCardBrand() + " ending in •••• " + pm.getLast4() + " (Tokenized)")
                    .build());
        }
        return dtos;
    }

    /**
     * Exports Customer Statement as CSV.
     */
    @Transactional(readOnly = true)
    public byte[] exportCustomerStatementCsv(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        Specification<LedgerTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (startDate != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            if (endDate != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<LedgerTransaction> transactions = ledgerRepository.findAll(spec);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            writer.println("UNIVERSAL COMMERCE - CUSTOMER ACCOUNT STATEMENT");
            writer.println("User ID," + userId);
            writer.println("Generated At," + LocalDateTime.now().toString());
            writer.println("Currency,USD");
            writer.println();
            writer.println("Transaction ID,Date & Time,Description,Reference Type,Reference ID,Type,Nature,Amount,Status");

            BigDecimal runningCredits = BigDecimal.ZERO;
            BigDecimal runningDebits = BigDecimal.ZERO;

            for (LedgerTransaction tx : transactions) {
                if (tx.getStatus() == TransactionStatus.POSTED) {
                    if (tx.getEntryType() == EntryType.CREDIT) runningCredits = runningCredits.add(tx.getAmount());
                    else runningDebits = runningDebits.add(tx.getAmount());
                }

                writer.printf("\"%d\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%.2f\",\"%s\"%n",
                        tx.getId(),
                        tx.getCreatedAt(),
                        tx.getDescription().replace("\"", "\"\""),
                        tx.getReferenceType(),
                        tx.getReferenceId(),
                        tx.getTransactionType(),
                        tx.getEntryType(),
                        tx.getAmount(),
                        tx.getStatus());
            }

            writer.println();
            writer.printf("Total Settled Credits,%.2f%n", runningCredits);
            writer.printf("Total Settled Debits,%.2f%n", runningDebits);
            writer.printf("Net Account Balance,%.2f%n", runningCredits.subtract(runningDebits));
        }

        return out.toByteArray();
    }

    /**
     * Exports Admin Master Ledger as CSV and logs audit event.
     */
    @Transactional
    public byte[] exportAdminLedgerCsv(
            Long adminId,
            String adminEmail,
            Long userId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            TransactionType type,
            TransactionStatus status) {

        Specification<LedgerTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) predicates.add(cb.equal(root.get("userId"), userId));
            if (startDate != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            if (endDate != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            if (type != null) predicates.add(cb.equal(root.get("transactionType"), type));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<LedgerTransaction> transactions = ledgerRepository.findAll(spec);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            writer.println("UNIVERSAL COMMERCE - MASTER ACCOUNTING SUBLEDGER EXPORT");
            writer.println("Exported By," + adminEmail);
            writer.println("Export Timestamp," + LocalDateTime.now().toString());
            writer.println();
            writer.println("TX_ID,User_ID,Customer_Email,Created_At,Type,Nature,Amount,Currency,Status,Description,Ref_Type,Ref_ID,Idempotency_Key,Reconciliation_Status,Provider_Ref");

            for (LedgerTransaction tx : transactions) {
                writer.printf("\"%d\",\"%d\",\"%s\",\"%s\",\"%s\",\"%s\",\"%.2f\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                        tx.getId(),
                        tx.getUserId(),
                        tx.getCustomerEmail() != null ? tx.getCustomerEmail() : "",
                        tx.getCreatedAt(),
                        tx.getTransactionType(),
                        tx.getEntryType(),
                        tx.getAmount(),
                        tx.getCurrency(),
                        tx.getStatus(),
                        tx.getDescription().replace("\"", "\"\""),
                        tx.getReferenceType(),
                        tx.getReferenceId(),
                        tx.getIdempotencyKey(),
                        tx.getReconciliationStatus(),
                        tx.getProviderReference() != null ? tx.getProviderReference() : "");
            }
        }

        recordAudit(
                null,
                adminId,
                adminEmail,
                "ADMIN_LEDGER_EXPORT_CSV",
                "Exported " + transactions.size() + " ledger transaction records to CSV",
                null,
                "RecordCount: " + transactions.size()
        );

        return out.toByteArray();
    }

    /**
     * Admin query: paginated audit logs.
     */
    @Transactional(readOnly = true)
    public Page<AccountingAuditLogDto> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable).map(this::toAuditDto);
    }

    private void recordAudit(
            Long transactionId,
            Long adminId,
            String adminEmail,
            String action,
            String reason,
            String beforeValue,
            String afterValue) {

        AccountingAuditLog logEntry = AccountingAuditLog.builder()
                .transactionId(transactionId)
                .adminId(adminId != null ? adminId : 0L)
                .adminEmail(adminEmail != null ? adminEmail : "SYSTEM")
                .action(action)
                .reason(reason)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .createdAt(LocalDateTime.now())
                .build();

        auditLogRepository.save(logEntry);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private LedgerTransactionDto toDto(LedgerTransaction entity) {
        TransactionStatus effectiveStatus = entity.getStatus();
        if (entity.getReversalOfId() == null && ledgerRepository.existsByReversalOfId(entity.getId())) {
            effectiveStatus = TransactionStatus.REVERSED;
        }

        return LedgerTransactionDto.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .transactionType(entity.getTransactionType())
                .entryType(entity.getEntryType())
                .amount(entity.getAmount().setScale(2, RoundingMode.HALF_UP))
                .currency(entity.getCurrency())
                .status(effectiveStatus)
                .description(entity.getDescription())
                .referenceType(entity.getReferenceType())
                .referenceId(entity.getReferenceId())
                .idempotencyKey(entity.getIdempotencyKey())
                .reversalOfId(entity.getReversalOfId())
                .reason(entity.getReason())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .customerEmail(entity.getCustomerEmail())
                .reconciliationStatus(entity.getReconciliationStatus() != null ? entity.getReconciliationStatus().name() : "RECONCILED")
                .reconciliationNotes(entity.getReconciliationNotes())
                .paymentProvider(entity.getPaymentProvider())
                .providerReference(entity.getProviderReference())
                .build();
    }

    private AccountingAuditLogDto toAuditDto(AccountingAuditLog entity) {
        return AccountingAuditLogDto.builder()
                .id(entity.getId())
                .transactionId(entity.getTransactionId())
                .adminId(entity.getAdminId())
                .adminEmail(entity.getAdminEmail())
                .action(entity.getAction())
                .reason(entity.getReason())
                .beforeValue(entity.getBeforeValue())
                .afterValue(entity.getAfterValue())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private RefundRequestDto toRefundDto(RefundRequest entity) {
        return RefundRequestDto.builder()
                .id(entity.getId())
                .orderId(entity.getOrder() != null ? entity.getOrder().getId() : null)
                .userId(entity.getUserId())
                .customerEmail(entity.getCustomerEmail())
                .amount(entity.getAmount().setScale(2, RoundingMode.HALF_UP))
                .currency(entity.getCurrency())
                .reason(entity.getReason())
                .status(entity.getStatus())
                .requestedBy(entity.getRequestedBy())
                .approvedBy(entity.getApprovedBy())
                .decisionReason(entity.getDecisionReason())
                .ledgerTransactionId(entity.getLedgerTransactionId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
