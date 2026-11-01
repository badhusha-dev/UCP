package com.ecommerce.procurement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProcurementService {
    private final JdbcTemplate jdbc;
    private final StockServiceClient stockClient;
    private final AccountingServiceClient accountingClient;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> suppliers(boolean includeArchived) {
        return jdbc.queryForList("SELECT id,supplier_code AS \"supplierCode\",name,contact_name AS \"contactName\",email,phone,payment_terms_days AS \"paymentTermsDays\",currency,address,notes,archived,created_at AS \"createdAt\",updated_at AS \"updatedAt\" FROM suppliers WHERE (?=TRUE OR archived=FALSE) ORDER BY name", includeArchived);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> supplier(long id) {
        return one("SELECT id,supplier_code AS \"supplierCode\",name,contact_name AS \"contactName\",email,phone,payment_terms_days AS \"paymentTermsDays\",currency,address,notes,archived,created_at AS \"createdAt\",updated_at AS \"updatedAt\" FROM suppliers WHERE id=?", id, "Supplier not found.");
    }

    @Transactional
    public Map<String, Object> createSupplier(Map<String, Object> request, String actor) {
        String code = requiredText(request, "supplierCode").toUpperCase(Locale.ROOT);
        String name = requiredText(request, "name");
        String currency = currency(request.getOrDefault("currency", "USD"));
        int terms = integer(request.getOrDefault("paymentTermsDays", 30), 30);
        if (code.length() > 40 || name.length() > 180 || terms < 0) badRequest("Supplier code/name or payment terms are invalid.");
        Long id;
        try {
            id = jdbc.queryForObject("INSERT INTO suppliers(supplier_code,name,contact_name,email,phone,payment_terms_days,currency,address,notes) VALUES(?,?,?,?,?,?,?,?,?) RETURNING id",
                    Long.class, code, name, text(request.get("contactName")), text(request.get("email")), text(request.get("phone")), terms, currency, text(request.get("address")), text(request.get("notes")));
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier code already exists.");
        }
        audit("SUPPLIER", id, "CREATE", actor, code);
        return supplier(Objects.requireNonNull(id));
    }

    @Transactional
    public Map<String, Object> updateSupplier(long id, Map<String, Object> request, String actor) {
        Map<String, Object> before = supplier(id);
        if (Boolean.TRUE.equals(before.get("archived"))) conflict("Restore the supplier before editing it.");
        String name = request.containsKey("name") ? requiredText(request, "name") : String.valueOf(before.get("name"));
        int terms = integer(request.getOrDefault("paymentTermsDays", before.get("paymentTermsDays")), 30);
        if (name.length() > 180 || terms < 0) badRequest("Supplier name or payment terms are invalid.");
        String nextCurrency = request.containsKey("currency") ? currency(request.get("currency")) : String.valueOf(before.get("currency"));
        jdbc.update("UPDATE suppliers SET name=?,contact_name=?,email=?,phone=?,payment_terms_days=?,currency=?,address=?,notes=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                name, nullable(request.get("contactName"), before.get("contactName")), nullable(request.get("email"), before.get("email")), nullable(request.get("phone"), before.get("phone")), terms, nextCurrency,
                nullable(request.get("address"), before.get("address")), nullable(request.get("notes"), before.get("notes")), id);
        audit("SUPPLIER", id, "UPDATE", actor, name);
        return supplier(id);
    }

    @Transactional
    public void archiveSupplier(long id, String actor) {
        supplier(id);
        int openOrders = jdbc.queryForObject("SELECT COUNT(*) FROM purchase_orders WHERE supplier_id=? AND status IN ('DRAFT','APPROVED','ORDERED','PARTIALLY_RECEIVED')", Integer.class, id);
        if (openOrders > 0) conflict("A supplier with open purchase orders cannot be archived.");
        jdbc.update("UPDATE suppliers SET archived=TRUE,updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        audit("SUPPLIER", id, "ARCHIVE", actor, "Supplier archived");
    }

    @Transactional
    public Map<String, Object> restoreSupplier(long id, String actor) {
        supplier(id);
        jdbc.update("UPDATE suppliers SET archived=FALSE,updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        audit("SUPPLIER", id, "RESTORE", actor, "Supplier restored");
        return supplier(id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> supplierProducts(long supplierId, boolean includeInactive) {
        supplier(supplierId);
        return jdbc.queryForList("SELECT id,supplier_id AS \"supplierId\",product_id AS \"productId\",supplier_sku AS \"supplierSku\",unit_cost AS \"unitCost\",lead_time_days AS \"leadTimeDays\",minimum_order_quantity AS \"minimumOrderQuantity\",preferred,active,created_at AS \"createdAt\",updated_at AS \"updatedAt\" FROM supplier_products WHERE supplier_id=? AND (?=TRUE OR active=TRUE) ORDER BY preferred DESC,product_id", supplierId, includeInactive);
    }

    @Transactional
    public Map<String, Object> createSupplierProduct(long supplierId, Map<String, Object> request, String actor) {
        Map<String, Object> vendor = supplier(supplierId);
        if (Boolean.TRUE.equals(vendor.get("archived"))) conflict("Restore this supplier before adding catalog items.");
        long productId = positiveLong(request.get("productId"), "Product ID must be positive.");
        BigDecimal unitCost = nonNegativeMoney(request.get("unitCost"), "Unit cost cannot be negative.");
        int lead = integer(request.getOrDefault("leadTimeDays", 7), 7);
        int minQty = integer(request.getOrDefault("minimumOrderQuantity", 1), 1);
        if (lead < 0 || minQty < 1) badRequest("Lead time must be non-negative and minimum quantity must be at least one.");
        Long id;
        try {
            id = jdbc.queryForObject("INSERT INTO supplier_products(supplier_id,product_id,supplier_sku,unit_cost,lead_time_days,minimum_order_quantity,preferred) VALUES(?,?,?,?,?,?,?) RETURNING id",
                    Long.class, supplierId, productId, text(request.get("supplierSku")), unitCost, lead, minQty, bool(request.get("preferred"), false));
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This product already exists in the supplier catalog.");
        }
        audit("SUPPLIER_PRODUCT", id, "CREATE", actor, "supplier=" + supplierId + ", product=" + productId);
        return supplierProduct(id);
    }

    @Transactional
    public Map<String, Object> updateSupplierProduct(long supplierId, long productId, Map<String, Object> request, String actor) {
        Map<String, Object> existing = supplierProductByProduct(supplierId, productId);
        BigDecimal unitCost = request.containsKey("unitCost") ? nonNegativeMoney(request.get("unitCost"), "Unit cost cannot be negative.") : money(existing.get("unitCost"));
        int lead = integer(request.getOrDefault("leadTimeDays", existing.get("leadTimeDays")), 7);
        int minQty = integer(request.getOrDefault("minimumOrderQuantity", existing.get("minimumOrderQuantity")), 1);
        if (lead < 0 || minQty < 1) badRequest("Lead time must be non-negative and minimum quantity must be at least one.");
        jdbc.update("UPDATE supplier_products SET supplier_sku=?,unit_cost=?,lead_time_days=?,minimum_order_quantity=?,preferred=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                nullable(request.get("supplierSku"), existing.get("supplierSku")), unitCost, lead, minQty, bool(request.get("preferred"), Boolean.TRUE.equals(existing.get("preferred"))), existing.get("id"));
        audit("SUPPLIER_PRODUCT", longValue(existing.get("id")), "UPDATE", actor, "Supplier catalog updated");
        return supplierProduct(longValue(existing.get("id")));
    }

    @Transactional
    public void archiveSupplierProduct(long supplierId, long productId, String actor) {
        Map<String, Object> existing = supplierProductByProduct(supplierId, productId);
        jdbc.update("UPDATE supplier_products SET active=FALSE,updated_at=CURRENT_TIMESTAMP WHERE id=?", existing.get("id"));
        audit("SUPPLIER_PRODUCT", longValue(existing.get("id")), "ARCHIVE", actor, "Catalog item archived");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> purchaseOrders(String status) {
        String normalized = status == null || status.isBlank() ? null : status.toUpperCase(Locale.ROOT);
        return jdbc.queryForList("SELECT p.id,p.po_number AS \"poNumber\",p.supplier_id AS \"supplierId\",s.name AS \"supplierName\",p.status,p.currency,p.subtotal,p.expected_date AS \"expectedDate\",p.created_by AS \"createdBy\",p.approved_by AS \"approvedBy\",p.ordered_at AS \"orderedAt\",p.received_at AS \"receivedAt\",p.created_at AS \"createdAt\",p.updated_at AS \"updatedAt\",(SELECT COUNT(*) FROM purchase_order_lines l WHERE l.purchase_order_id=p.id) AS \"lineCount\" FROM purchase_orders p JOIN suppliers s ON s.id=p.supplier_id WHERE (? IS NULL OR p.status=?) ORDER BY p.created_at DESC LIMIT 250", normalized, normalized);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> purchaseOrder(long id) {
        Map<String, Object> order = one("SELECT p.id,p.po_number AS \"poNumber\",p.supplier_id AS \"supplierId\",s.name AS \"supplierName\",p.status,p.currency,p.subtotal,p.expected_date AS \"expectedDate\",p.note,p.created_by AS \"createdBy\",p.approved_by AS \"approvedBy\",p.approved_at AS \"approvedAt\",p.ordered_at AS \"orderedAt\",p.received_at AS \"receivedAt\",p.created_at AS \"createdAt\",p.updated_at AS \"updatedAt\" FROM purchase_orders p JOIN suppliers s ON s.id=p.supplier_id WHERE p.id=?", id, "Purchase order not found.");
        order.put("lines", jdbc.queryForList("SELECT id,supplier_product_id AS \"supplierProductId\",product_id AS \"productId\",sku,description,quantity_ordered AS \"quantityOrdered\",quantity_received AS \"quantityReceived\",unit_cost AS \"unitCost\",line_total AS \"lineTotal\" FROM purchase_order_lines WHERE purchase_order_id=? ORDER BY id", id));
        return order;
    }

    @Transactional
    public Map<String, Object> createPurchaseOrder(Map<String, Object> request, String actor) {
        long supplierId = positiveLong(request.get("supplierId"), "Supplier is required.");
        Map<String, Object> vendor = supplier(supplierId);
        if (Boolean.TRUE.equals(vendor.get("archived"))) conflict("Archived suppliers cannot receive new purchase orders.");
        return insertPurchaseOrder(supplierId, maps(request.get("lines")), date(request.get("expectedDate")), text(request.get("note")), actor);
    }

    @Transactional
    public Map<String, Object> updatePurchaseOrder(long id, Map<String, Object> request, String actor) {
        Map<String, Object> before = purchaseOrder(id);
        if (!"DRAFT".equals(before.get("status"))) conflict("Only draft purchase orders can be edited.");
        if (request.containsKey("expectedDate")) jdbc.update("UPDATE purchase_orders SET expected_date=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", date(request.get("expectedDate")), id);
        if (request.containsKey("note")) jdbc.update("UPDATE purchase_orders SET note=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", text(request.get("note")), id);
        if (request.containsKey("lines")) {
            List<Map<String, Object>> lines = maps(request.get("lines"));
            if (lines.isEmpty()) badRequest("A purchase order must contain at least one line.");
            jdbc.update("DELETE FROM purchase_order_lines WHERE purchase_order_id=?", id);
            BigDecimal total = insertPurchaseOrderLines(id, longValue(before.get("supplierId")), lines);
            jdbc.update("UPDATE purchase_orders SET subtotal=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", total, id);
        }
        audit("PURCHASE_ORDER", id, "UPDATE", actor, "Draft order updated");
        return purchaseOrder(id);
    }

    @Transactional
    public Map<String, Object> approvePurchaseOrder(long id, String actor) {
        Map<String, Object> order = purchaseOrder(id);
        if (!"DRAFT".equals(order.get("status"))) conflict("Only draft purchase orders can be approved.");
        jdbc.update("UPDATE purchase_orders SET status='APPROVED',approved_by=?,approved_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?", actorOrSystem(actor), id);
        audit("PURCHASE_ORDER", id, "APPROVE", actor, String.valueOf(order.get("poNumber")));
        return purchaseOrder(id);
    }

    @Transactional
    public Map<String, Object> issuePurchaseOrder(long id, String actor) {
        Map<String, Object> order = purchaseOrder(id);
        if (!"APPROVED".equals(order.get("status"))) conflict("Approve the purchase order before issuing it to the supplier.");
        jdbc.update("UPDATE purchase_orders SET status='ORDERED',ordered_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        audit("PURCHASE_ORDER", id, "ISSUE", actor, String.valueOf(order.get("poNumber")));
        return purchaseOrder(id);
    }

    @Transactional
    public Map<String, Object> cancelPurchaseOrder(long id, String actor) {
        Map<String, Object> order = purchaseOrder(id);
        if (!Set.of("DRAFT", "APPROVED", "ORDERED").contains(String.valueOf(order.get("status")))) conflict("This purchase order cannot be cancelled in its current state.");
        jdbc.update("UPDATE purchase_orders SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        audit("PURCHASE_ORDER", id, "CANCEL", actor, String.valueOf(order.get("poNumber")));
        return purchaseOrder(id);
    }

    @Transactional
    public Map<String, Object> receivePurchaseOrder(long id, Map<String, Object> request, String actor) {
        Map<String, Object> order = purchaseOrder(id);
        String receiptNumber = text(request.get("receiptNumber"));
        if (receiptNumber.isBlank()) receiptNumber = "RCV-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        if (receiptNumber.length() > 100) badRequest("Receipt reference is too long.");
        List<Map<String, Object>> receiptLines = maps(request.get("lines"));
        if (receiptLines.isEmpty()) badRequest("At least one received quantity is required.");
        Integer existingReceipt = jdbc.query("SELECT purchase_order_id FROM goods_receipts WHERE receipt_number=?", (rs, row) -> rs.getInt(1), receiptNumber).stream().findFirst().orElse(null);
        if (existingReceipt != null) {
            if (existingReceipt != id) conflict("Receipt reference already belongs to another purchase order.");
            Map<String, Object> replay = new LinkedHashMap<>();
            replay.put("receiptNumber", receiptNumber); replay.put("purchaseOrder", purchaseOrder(id)); replay.put("idempotentReplay", true);
            return replay;
        }
        String status = String.valueOf(order.get("status"));
        if (!Set.of("ORDERED", "PARTIALLY_RECEIVED").contains(status)) conflict("Only issued purchase orders can receive stock.");
        Long receiptId = jdbc.queryForObject("INSERT INTO goods_receipts(receipt_number,purchase_order_id,received_by) VALUES(?,?,?) RETURNING id", Long.class, receiptNumber, id, actorOrSystem(actor));
        Set<Long> seen = new HashSet<>();
        for (Map<String, Object> line : receiptLines) {
            long productId = positiveLong(line.get("productId"), "Product ID must be positive.");
            int quantity = integer(line.get("quantity"), 0);
            if (quantity <= 0 || !seen.add(productId)) badRequest("Receive quantities must be positive and each product may appear once per receipt.");
            Map<String, Object> poLine = jdbc.query("SELECT id,quantity_ordered AS \"quantityOrdered\",quantity_received AS \"quantityReceived\",unit_cost AS \"unitCost\" FROM purchase_order_lines WHERE purchase_order_id=? AND product_id=? FOR UPDATE",
                    rs -> rs.next() ? rowMap(rs) : null, id, productId);
            if (poLine == null) badRequest("Product " + productId + " is not on this purchase order.");
            int remaining = integer(poLine.get("quantityOrdered"), 0) - integer(poLine.get("quantityReceived"), 0);
            if (quantity > remaining) conflict("Received quantity exceeds the remaining quantity for product " + productId + ".");
            String stockReference = receiptNumber + ":" + productId;
            try {
                stockClient.receive(productId, quantity, money(poLine.get("unitCost")), stockReference,
                        "Purchase order " + order.get("poNumber") + " receipt " + receiptNumber);
            } catch (Exception exception) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stock service could not confirm receipt for product " + productId + ". Retry using the same receipt reference.");
            }
            jdbc.update("INSERT INTO goods_receipt_lines(receipt_id,product_id,quantity,stock_reference) VALUES(?,?,?,?)", receiptId, productId, quantity, stockReference);
            jdbc.update("UPDATE purchase_order_lines SET quantity_received=quantity_received+? WHERE purchase_order_id=? AND product_id=?", quantity, id, productId);
        }
        int remainingLines = jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order_lines WHERE purchase_order_id=? AND quantity_received<quantity_ordered", Integer.class, id);
        String next = remainingLines == 0 ? "RECEIVED" : "PARTIALLY_RECEIVED";
        jdbc.update("UPDATE purchase_orders SET status=?,received_at=CASE WHEN ?='RECEIVED' THEN CURRENT_TIMESTAMP ELSE received_at END,updated_at=CURRENT_TIMESTAMP WHERE id=?", next, next, id);
        audit("PURCHASE_ORDER", id, "RECEIVE", actor, receiptNumber);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("receiptNumber", receiptNumber); result.put("purchaseOrder", purchaseOrder(id)); result.put("idempotentReplay", false);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> supplierInvoices(String status) {
        String normalized = status == null || status.isBlank() ? null : status.toUpperCase(Locale.ROOT);
        return jdbc.queryForList("SELECT i.id,i.invoice_number AS \"invoiceNumber\",i.supplier_id AS \"supplierId\",s.name AS \"supplierName\",i.purchase_order_id AS \"purchaseOrderId\",p.po_number AS \"poNumber\",i.status,i.currency,i.subtotal,i.tax_amount AS \"taxAmount\",i.total_amount AS \"totalAmount\",i.issue_date AS \"issueDate\",i.due_date AS \"dueDate\",i.accounting_entry_id AS \"accountingEntryId\",i.created_at AS \"createdAt\" FROM supplier_invoices i JOIN suppliers s ON s.id=i.supplier_id JOIN purchase_orders p ON p.id=i.purchase_order_id WHERE (? IS NULL OR i.status=?) ORDER BY i.created_at DESC LIMIT 250", normalized, normalized);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> supplierInvoice(long id) {
        Map<String, Object> invoice = one("SELECT i.id,i.invoice_number AS \"invoiceNumber\",i.supplier_id AS \"supplierId\",s.name AS \"supplierName\",i.purchase_order_id AS \"purchaseOrderId\",p.po_number AS \"poNumber\",i.status,i.currency,i.subtotal,i.tax_amount AS \"taxAmount\",i.total_amount AS \"totalAmount\",i.issue_date AS \"issueDate\",i.due_date AS \"dueDate\",i.accounting_entry_id AS \"accountingEntryId\",i.created_by AS \"createdBy\",i.created_at AS \"createdAt\" FROM supplier_invoices i JOIN suppliers s ON s.id=i.supplier_id JOIN purchase_orders p ON p.id=i.purchase_order_id WHERE i.id=?", id, "Supplier invoice not found.");
        invoice.put("lines", jdbc.queryForList("SELECT product_id AS \"productId\",quantity,unit_cost AS \"unitCost\",line_total AS \"lineTotal\" FROM supplier_invoice_lines WHERE supplier_invoice_id=? ORDER BY id", id));
        return invoice;
    }

    @Transactional
    public Map<String, Object> createSupplierInvoice(long purchaseOrderId, Map<String, Object> request, String actor) {
        Map<String, Object> order = purchaseOrder(purchaseOrderId);
        if (Set.of("DRAFT", "APPROVED", "CANCELLED").contains(String.valueOf(order.get("status")))) conflict("Supplier invoices require an issued purchase order with received stock.");
        String invoiceNumber = requiredText(request, "invoiceNumber");
        if (invoiceNumber.length() > 80) badRequest("Supplier invoice number is too long.");
        List<Map<String, Object>> lines = maps(request.get("lines"));
        if (lines.isEmpty()) badRequest("A supplier invoice must have at least one line.");
        Map<Long, Map<String, Object>> orderLines = new HashMap<>();
        for (Map<String, Object> row : maps(order.get("lines"))) orderLines.put(longValue(row.get("productId")), row);
        Set<Long> seen = new HashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        List<Map<String, Object>> calculated = new ArrayList<>();
        for (Map<String, Object> line : lines) {
            long productId = positiveLong(line.get("productId"), "Product ID must be positive.");
            BigDecimal quantity = decimal(line.get("quantity"));
            BigDecimal unitCost = nonNegativeMoney(line.get("unitCost"), "Invoice unit cost cannot be negative.");
            if (quantity.signum() <= 0 || !seen.add(productId)) badRequest("Invoice quantities must be positive and each product may appear once per invoice.");
            Map<String, Object> poLine = orderLines.get(productId);
            if (poLine == null) badRequest("Product " + productId + " is not on the purchase order.");
            BigDecimal poCost = money(poLine.get("unitCost"));
            if (unitCost.compareTo(poCost) != 0) conflict("Invoice cost does not match the approved purchase order for product " + productId + ".");
            BigDecimal alreadyBilled = jdbc.queryForObject("SELECT COALESCE(SUM(il.quantity),0) FROM supplier_invoice_lines il JOIN supplier_invoices i ON i.id=il.supplier_invoice_id WHERE i.purchase_order_id=? AND il.product_id=? AND i.status<>'VOID'", BigDecimal.class, purchaseOrderId, productId);
            BigDecimal received = BigDecimal.valueOf(integer(poLine.get("quantityReceived"), 0));
            if (alreadyBilled.add(quantity).compareTo(received) > 0) conflict("Invoice quantity exceeds received quantity for product " + productId + ".");
            BigDecimal lineTotal = quantity.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            calculated.add(Map.of("productId", productId, "quantity", quantity, "unitCost", unitCost, "lineTotal", lineTotal));
        }
        BigDecimal tax = nonNegativeMoney(request.getOrDefault("taxAmount", BigDecimal.ZERO), "Tax cannot be negative.");
        BigDecimal total = subtotal.add(tax).setScale(4, RoundingMode.HALF_UP);
        LocalDate issueDate = date(request.get("issueDate"));
        if (issueDate == null) issueDate = LocalDate.now();
        LocalDate dueDate = date(request.get("dueDate"));
        Long id;
        try {
            id = jdbc.queryForObject("INSERT INTO supplier_invoices(invoice_number,supplier_id,purchase_order_id,status,currency,subtotal,tax_amount,total_amount,issue_date,due_date,created_by) VALUES(?,?,?,'MATCHED',?,?,?,?,?,?,?) RETURNING id",
                    Long.class, invoiceNumber, order.get("supplierId"), purchaseOrderId, order.get("currency"), subtotal, tax, total, issueDate, dueDate, actorOrSystem(actor));
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This supplier invoice number has already been entered.");
        }
        for (Map<String, Object> line : calculated) jdbc.update("INSERT INTO supplier_invoice_lines(supplier_invoice_id,product_id,quantity,unit_cost,line_total) VALUES(?,?,?,?,?)",
                id, line.get("productId"), line.get("quantity"), line.get("unitCost"), line.get("lineTotal"));
        audit("SUPPLIER_INVOICE", id, "MATCHED", actor, invoiceNumber + " matched to " + order.get("poNumber"));
        return supplierInvoice(Objects.requireNonNull(id));
    }

    @Transactional
    public Map<String, Object> approveSupplierInvoice(long id, String actor) {
        Map<String, Object> invoice = supplierInvoice(id);
        if ("APPROVED".equals(invoice.get("status"))) return invoice;
        if (!"MATCHED".equals(invoice.get("status"))) conflict("Only matched supplier invoices can be approved.");
        Map<String, Object> accountingRequest = new LinkedHashMap<>();
        accountingRequest.put("idempotencyKey", "procurement-supplier-invoice-" + id);
        accountingRequest.put("invoiceNumber", invoice.get("invoiceNumber"));
        accountingRequest.put("supplierId", invoice.get("supplierId"));
        accountingRequest.put("supplierName", invoice.get("supplierName"));
        accountingRequest.put("purchaseOrderNumber", invoice.get("poNumber"));
        accountingRequest.put("currency", invoice.get("currency"));
        accountingRequest.put("subtotal", invoice.get("subtotal"));
        accountingRequest.put("taxAmount", invoice.get("taxAmount"));
        accountingRequest.put("totalAmount", invoice.get("totalAmount"));
        accountingRequest.put("issueDate", invoice.get("issueDate"));
        accountingRequest.put("dueDate", invoice.get("dueDate"));
        accountingRequest.put("postedBy", actorOrSystem(actor));
        Map<String, Object> posted;
        try {
            posted = accountingClient.recordSupplierPayable(accountingRequest);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Accounting could not post the supplier payable. The invoice remains matched and can be retried.");
        }
        Long entryId = posted == null ? null : longValue(posted.get("entryId"));
        jdbc.update("UPDATE supplier_invoices SET status='APPROVED',accounting_entry_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", entryId, id);
        audit("SUPPLIER_INVOICE", id, "APPROVE", actor, "Accounts payable journal entry " + entryId);
        return supplierInvoice(id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> replenishmentSuggestions() {
        List<Map<String, Object>> lowStock;
        try {
            lowStock = stockClient.lowStockItems();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stock service is unavailable for replenishment suggestions.");
        }
        if (lowStock == null) return List.of();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> item : lowStock) {
            long productId = longValue(item.get("productId"));
            List<Map<String, Object>> mappings = jdbc.queryForList("SELECT sp.id AS \"supplierProductId\",sp.supplier_id AS \"supplierId\",s.name AS \"supplierName\",sp.supplier_sku AS \"supplierSku\",sp.unit_cost AS \"unitCost\",sp.lead_time_days AS \"leadTimeDays\",sp.minimum_order_quantity AS \"minimumOrderQuantity\",sp.preferred FROM supplier_products sp JOIN suppliers s ON s.id=sp.supplier_id WHERE sp.product_id=? AND sp.active=TRUE AND s.archived=FALSE ORDER BY sp.preferred DESC,sp.lead_time_days,s.name", productId);
            Map<String, Object> suggestion = new LinkedHashMap<>(item);
            suggestion.put("supplierOptions", mappings);
            if (!mappings.isEmpty()) {
                Map<String, Object> preferred = mappings.get(0);
                int available = integer(item.get("availableQuantity"), 0);
                int threshold = integer(item.get("reorderThreshold"), 0);
                int minimum = integer(preferred.get("minimumOrderQuantity"), 1);
                suggestion.put("suggestedQuantity", Math.max(minimum, Math.max(1, threshold * 2 - available)));
                suggestion.put("preferredSupplier", preferred);
            } else suggestion.put("suggestedQuantity", Math.max(1, integer(item.get("reorderThreshold"), 1) * 2 - integer(item.get("availableQuantity"), 0)));
            results.add(suggestion);
        }
        return results;
    }

    @Transactional
    public Map<String, Object> createDraftFromSuggestion(long productId, Long supplierId, String actor) {
        Map<String, Object> item;
        try {
            item = stockClient.lowStockItems().stream().filter(row -> longValue(row.get("productId")) == productId).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product is not currently in the low-stock queue."));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stock service is unavailable for replenishment suggestions.");
        }
        List<Map<String, Object>> mappings = jdbc.queryForList("SELECT sp.id AS \"supplierProductId\",sp.supplier_id AS \"supplierId\",sp.minimum_order_quantity AS \"minimumOrderQuantity\" FROM supplier_products sp JOIN suppliers s ON s.id=sp.supplier_id WHERE sp.product_id=? AND sp.active=TRUE AND s.archived=FALSE AND (? IS NULL OR sp.supplier_id=?) ORDER BY sp.preferred DESC,sp.lead_time_days,s.name", productId, supplierId, supplierId);
        if (mappings.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Add this product to an active supplier catalog before creating a purchase order.");
        Map<String, Object> mapping = mappings.get(0);
        int available = integer(item.get("availableQuantity"), 0);
        int threshold = integer(item.get("reorderThreshold"), 0);
        int quantity = Math.max(integer(mapping.get("minimumOrderQuantity"), 1), Math.max(1, threshold * 2 - available));
        return insertPurchaseOrder(longValue(mapping.get("supplierId")), List.of(Map.of("supplierProductId", mapping.get("supplierProductId"), "quantity", quantity)), null,
                "Draft created from low-stock recommendation for product " + productId, actor);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeSuppliers", jdbc.queryForObject("SELECT COUNT(*) FROM suppliers WHERE archived=FALSE", Long.class));
        result.put("openPurchaseOrders", jdbc.queryForObject("SELECT COUNT(*) FROM purchase_orders WHERE status IN ('APPROVED','ORDERED','PARTIALLY_RECEIVED')", Long.class));
        result.put("draftPurchaseOrders", jdbc.queryForObject("SELECT COUNT(*) FROM purchase_orders WHERE status='DRAFT'", Long.class));
        result.put("matchedInvoices", jdbc.queryForObject("SELECT COUNT(*) FROM supplier_invoices WHERE status='MATCHED'", Long.class));
        result.put("approvedPayables", jdbc.queryForObject("SELECT COALESCE(SUM(total_amount),0) FROM supplier_invoices WHERE status='APPROVED'", BigDecimal.class));
        return result;
    }

    private Map<String, Object> insertPurchaseOrder(long supplierId, List<Map<String, Object>> lines, LocalDate expectedDate, String note, String actor) {
        if (lines == null || lines.isEmpty()) badRequest("A purchase order must contain at least one line.");
        Map<String, Object> vendor = supplier(supplierId);
        if (Boolean.TRUE.equals(vendor.get("archived"))) conflict("Archived suppliers cannot receive new purchase orders.");
        String number = "PO-" + LocalDate.now().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        Long id = jdbc.queryForObject("INSERT INTO purchase_orders(po_number,supplier_id,status,currency,expected_date,note,created_by) VALUES(?,?,'DRAFT',?,?,?,?) RETURNING id",
                Long.class, number, supplierId, vendor.get("currency"), expectedDate, note, actorOrSystem(actor));
        BigDecimal subtotal = insertPurchaseOrderLines(Objects.requireNonNull(id), supplierId, lines);
        jdbc.update("UPDATE purchase_orders SET subtotal=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", subtotal, id);
        audit("PURCHASE_ORDER", id, "CREATE", actor, number);
        return purchaseOrder(id);
    }

    private BigDecimal insertPurchaseOrderLines(long orderId, long supplierId, List<Map<String, Object>> lines) {
        Set<Long> seen = new HashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map<String, Object> line : lines) {
            long mappingId = positiveLong(line.get("supplierProductId"), "Choose a supplier catalog item for each purchase order line.");
            int quantity = integer(line.get("quantity"), 0);
            if (quantity <= 0) badRequest("Ordered quantity must be greater than zero.");
            Map<String, Object> mapping = jdbc.query("SELECT id,product_id AS \"productId\",supplier_sku AS \"supplierSku\",unit_cost AS \"unitCost\",active FROM supplier_products WHERE id=? AND supplier_id=?",
                    rs -> rs.next() ? rowMap(rs) : null, mappingId, supplierId);
            if (mapping == null || !Boolean.TRUE.equals(mapping.get("active"))) badRequest("Supplier catalog item is missing or archived.");
            long productId = longValue(mapping.get("productId"));
            if (!seen.add(productId)) badRequest("A purchase order can contain each product only once.");
            BigDecimal unitCost = money(mapping.get("unitCost"));
            BigDecimal total = unitCost.multiply(BigDecimal.valueOf(quantity)).setScale(4, RoundingMode.HALF_UP);
            jdbc.update("INSERT INTO purchase_order_lines(purchase_order_id,supplier_product_id,product_id,sku,description,quantity_ordered,unit_cost,line_total) VALUES(?,?,?,?,?,?,?,?)",
                    orderId, mappingId, productId, text(mapping.get("supplierSku")), "Product " + productId, quantity, unitCost, total);
            subtotal = subtotal.add(total);
        }
        return subtotal.setScale(4, RoundingMode.HALF_UP);
    }

    private Map<String, Object> supplierProduct(long id) {
        return one("SELECT id,supplier_id AS \"supplierId\",product_id AS \"productId\",supplier_sku AS \"supplierSku\",unit_cost AS \"unitCost\",lead_time_days AS \"leadTimeDays\",minimum_order_quantity AS \"minimumOrderQuantity\",preferred,active,created_at AS \"createdAt\",updated_at AS \"updatedAt\" FROM supplier_products WHERE id=?", id, "Supplier catalog item not found.");
    }

    private Map<String, Object> supplierProductByProduct(long supplierId, long productId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,supplier_id AS \"supplierId\",product_id AS \"productId\",supplier_sku AS \"supplierSku\",unit_cost AS \"unitCost\",lead_time_days AS \"leadTimeDays\",minimum_order_quantity AS \"minimumOrderQuantity\",preferred,active FROM supplier_products WHERE supplier_id=? AND product_id=?", supplierId, productId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier catalog item not found.");
        return rows.get(0);
    }

    private void audit(String entity, long id, String action, String actor, String details) {
        jdbc.update("INSERT INTO procurement_audit(entity_type,entity_id,action,actor,details) VALUES(?,?,?,?,?)", entity, id, action, actorOrSystem(actor), details);
    }

    private Map<String, Object> one(String sql, Object id, String message) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        return rows.get(0);
    }

    private Map<String, Object> rowMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
        return row;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) badRequest("Invalid list item.");
            Map<String, Object> row = new LinkedHashMap<>();
            ((Map<?, ?>) item).forEach((key, val) -> row.put(String.valueOf(key), val));
            result.add(row);
        }
        return result;
    }

    private String requiredText(Map<String, Object> request, String key) {
        String value = text(request.get(key)).trim();
        if (value.isBlank()) badRequest(key + " is required.");
        return value;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private Object nullable(Object input, Object fallback) { return input == null ? fallback : text(input).isBlank() ? null : text(input); }
    private String actorOrSystem(String actor) { return actor == null || actor.isBlank() ? "procurement-service" : actor; }

    private int integer(Object value, int fallback) {
        if (value == null) return fallback;
        try { return new BigDecimal(String.valueOf(value)).intValueExact(); }
        catch (Exception exception) { badRequest("Expected a whole number."); return fallback; }
    }

    private long positiveLong(Object value, String message) {
        long parsed = longValue(value);
        if (parsed <= 0) badRequest(message);
        return parsed;
    }

    private long longValue(Object value) {
        if (value == null) return 0;
        try { return Long.parseLong(String.valueOf(value)); }
        catch (Exception exception) { badRequest("Expected a valid numeric ID."); return 0; }
    }

    private BigDecimal decimal(Object value) {
        try { return new BigDecimal(String.valueOf(value)); }
        catch (Exception exception) { badRequest("Expected a valid decimal amount."); return BigDecimal.ZERO; }
    }

    private BigDecimal money(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)); }
    private BigDecimal nonNegativeMoney(Object value, String message) {
        BigDecimal result = decimal(value).setScale(4, RoundingMode.HALF_UP);
        if (result.signum() < 0) badRequest(message);
        return result;
    }

    private String currency(Object value) {
        String code = text(value).toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{3}")) badRequest("Currency must be a three-letter code.");
        return code;
    }

    private boolean bool(Object value, boolean fallback) { return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value)); }

    private LocalDate date(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return value instanceof LocalDate d ? d : LocalDate.parse(String.valueOf(value).substring(0, 10)); }
        catch (Exception exception) { badRequest("Dates must use ISO format YYYY-MM-DD."); return null; }
    }

    private void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
