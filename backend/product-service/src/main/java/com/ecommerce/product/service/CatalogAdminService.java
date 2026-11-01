package com.ecommerce.product.service;

import com.ecommerce.product.dto.ProductAdminRequest;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.ProductAuditLog;
import com.ecommerce.product.repository.ProductAuditLogRepository;
import com.ecommerce.product.repository.ProductCategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.security.CatalogPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Tenant-scoped catalog administration. Every read and write is bound to the caller's tenant and audited. */
@Service
@RequiredArgsConstructor
public class CatalogAdminService {
    private static final Pattern SKU_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductAuditLogRepository auditRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<Product> search(CatalogPrincipal actor, String query, Boolean active, String sortBy, int page, int size) {
        Sort sort = switch (sortBy == null ? "name" : sortBy) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "sku" -> Sort.by(Sort.Direction.ASC, "sku");
            default -> Sort.by(Sort.Direction.ASC, "name");
        };
        String normalized = query == null || query.isBlank() ? null : query.trim();
        return productRepository.searchAdmin(actor.tenantId(), normalized, active,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort));
    }

    @Transactional(readOnly = true)
    public Product get(CatalogPrincipal actor, Long id) {
        return find(actor.tenantId(), id);
    }

    @Transactional
    public Product create(CatalogPrincipal actor, ProductAdminRequest request) {
        Product product = new Product();
        product.setTenantId(actor.tenantId());
        apply(actor.tenantId(), product, request);
        product.setStockQuantity(0);
        product.setActive(true);
        Product saved = productRepository.save(product);
        audit(actor, saved.getId(), "CREATE", null, snapshot(saved));
        return saved;
    }

    @Transactional
    public Product update(CatalogPrincipal actor, Long id, ProductAdminRequest request) {
        Product product = find(actor.tenantId(), id);
        String before = snapshot(product);
        apply(actor.tenantId(), product, request);
        Product saved = productRepository.save(product);
        audit(actor, saved.getId(), "UPDATE", before, snapshot(saved));
        return saved;
    }

    @Transactional
    public void archive(CatalogPrincipal actor, Long id) {
        setActive(actor, id, false, "ARCHIVE");
    }

    @Transactional
    public Product restore(CatalogPrincipal actor, Long id) {
        return setActive(actor, id, true, "RESTORE");
    }

    @Transactional(readOnly = true)
    public List<ProductAuditLog> history(CatalogPrincipal actor, Long id) {
        find(actor.tenantId(), id);
        return auditRepository.findByTenantIdAndProductIdOrderByIdDesc(actor.tenantId(), id);
    }

    private Product setActive(CatalogPrincipal actor, Long id, boolean active, String action) {
        Product product = find(actor.tenantId(), id);
        if (product.getActive() == active) return product; // idempotent: repeated requests do not add audit noise
        String before = snapshot(product);
        product.setActive(active);
        Product saved = productRepository.save(product);
        audit(actor, saved.getId(), action, before, snapshot(saved));
        return saved;
    }

    // A product in another tenant is indistinguishable from a missing one.
    private Product find(UUID tenantId, Long id) {
        return productRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    private void apply(UUID tenantId, Product product, ProductAdminRequest request) {
        String name = request.getName();
        Double price = request.getPrice();
        if (name == null || name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product name is required.");
        if (price == null || !Double.isFinite(price) || price < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be a non-negative number.");
        if (request.getCostPrice() != null && request.getCostPrice().signum() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cost price cannot be negative.");
        if (request.getReorderThreshold() != null && request.getReorderThreshold() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reorder threshold cannot be negative.");

        String sku = request.getSku() == null || request.getSku().isBlank() ? null : request.getSku().trim();
        if (sku != null) {
            if (!SKU_PATTERN.matcher(sku).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU may contain letters, digits, '.', '_' and '-' (max 64).");
            boolean changed = product.getSku() == null || !product.getSku().equalsIgnoreCase(sku);
            if (changed && productRepository.existsByTenantIdAndSkuIgnoreCase(tenantId, sku)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists for this client.");
            }
        }

        product.setSku(sku);
        product.setName(name.trim());
        product.setDescription(request.getDescription() == null || request.getDescription().isBlank() ? null : request.getDescription().trim());
        product.setPrice(price);
        product.setCostPrice(request.getCostPrice());
        product.setReorderThreshold(request.getReorderThreshold() == null ? 10 : request.getReorderThreshold());
        product.setImageUrl(request.getImageUrl() == null || request.getImageUrl().isBlank() ? null : request.getImageUrl().trim());
        Long categoryId = request.getCategoryId();
        product.setCategory(categoryId == null ? null : categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found.")));
    }

    private void audit(CatalogPrincipal actor, Long productId, String action, String before, String after) {
        auditRepository.save(ProductAuditLog.builder().tenantId(actor.tenantId()).productId(productId).action(action)
                .actorId(actor.userId()).actorEmail(actor.email()).beforeValue(before).afterValue(after).build());
    }

    private String snapshot(Product p) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sku", p.getSku());
        fields.put("name", p.getName());
        fields.put("description", p.getDescription());
        fields.put("price", p.getPrice());
        fields.put("costPrice", p.getCostPrice());
        fields.put("reorderThreshold", p.getReorderThreshold());
        fields.put("categoryId", p.getCategory() == null ? null : p.getCategory().getId());
        fields.put("imageUrl", p.getImageUrl());
        fields.put("active", p.getActive());
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize audit snapshot", e);
        }
    }
}
