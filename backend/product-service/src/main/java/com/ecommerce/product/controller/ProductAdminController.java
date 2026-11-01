package com.ecommerce.product.controller;

import com.ecommerce.product.dto.ProductAdminRequest;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.ProductAuditLog;
import com.ecommerce.product.security.CatalogPrincipal;
import com.ecommerce.product.service.CatalogAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Tenant-scoped catalog administration; authenticated by CatalogTenantAuthenticationFilter. */
@RestController
@RequestMapping("/api/products/admin")
@RequiredArgsConstructor
public class ProductAdminController {
    private static final String READ = "catalog:read";
    private static final String MANAGE = "catalog:manage";

    private final CatalogAdminService catalogService;

    @GetMapping
    public ResponseEntity<Page<Product>> search(@AuthenticationPrincipal CatalogPrincipal actor,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) Boolean active,
                                                @RequestParam(required = false) String sort,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "25") int size) {
        require(actor, READ);
        return ResponseEntity.ok(catalogService.search(actor, q, active, sort, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> get(@AuthenticationPrincipal CatalogPrincipal actor, @PathVariable Long id) {
        require(actor, READ);
        return ResponseEntity.ok(catalogService.get(actor, id));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<ProductAuditLog>> history(@AuthenticationPrincipal CatalogPrincipal actor, @PathVariable Long id) {
        require(actor, READ);
        return ResponseEntity.ok(catalogService.history(actor, id));
    }

    @PostMapping
    public ResponseEntity<Product> create(@AuthenticationPrincipal CatalogPrincipal actor, @Valid @RequestBody ProductAdminRequest request) {
        require(actor, MANAGE);
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.create(actor, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@AuthenticationPrincipal CatalogPrincipal actor, @PathVariable Long id,
                                          @Valid @RequestBody ProductAdminRequest request) {
        require(actor, MANAGE);
        return ResponseEntity.ok(catalogService.update(actor, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@AuthenticationPrincipal CatalogPrincipal actor, @PathVariable Long id) {
        require(actor, MANAGE);
        catalogService.archive(actor, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<Product> restore(@AuthenticationPrincipal CatalogPrincipal actor, @PathVariable Long id) {
        require(actor, MANAGE);
        return ResponseEntity.ok(catalogService.restore(actor, id));
    }

    private static void require(CatalogPrincipal actor, String permission) {
        if (actor == null || !actor.hasPermission(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission " + permission + " is required.");
        }
    }
}
