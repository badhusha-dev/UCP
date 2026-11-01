package com.ecommerce.procurement.controller;

import com.ecommerce.procurement.service.ProcurementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/procurement")
@RequiredArgsConstructor
@Tag(name = "Supplier & Procurement", description = "Supplier catalog, purchase orders, stock receiving, and invoice matching")
@SecurityRequirement(name = "bearerAuth")
public class ProcurementController {
    private final ProcurementService service;

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestHeader(value = "X-User-Role", required = false) String role) {
        requireStaff(role); return service.dashboard();
    }

    @GetMapping("/suppliers")
    public List<Map<String, Object>> suppliers(@RequestHeader(value = "X-User-Role", required = false) String role,
                                               @RequestParam(defaultValue = "false") boolean includeArchived) {
        requireStaff(role); return service.suppliers(includeArchived);
    }

    @PostMapping("/suppliers")
    public ResponseEntity<Map<String, Object>> createSupplier(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                              @RequestHeader(value = "X-User-Email", required = false) String actor,
                                                              @RequestBody Map<String, Object> request) {
        requireStaff(role); return ResponseEntity.status(HttpStatus.CREATED).body(service.createSupplier(request, actor));
    }

    @PutMapping("/suppliers/{id}")
    public Map<String, Object> updateSupplier(@RequestHeader(value = "X-User-Role", required = false) String role,
                                              @RequestHeader(value = "X-User-Email", required = false) String actor,
                                              @PathVariable long id, @RequestBody Map<String, Object> request) {
        requireStaff(role); return service.updateSupplier(id, request, actor);
    }

    @DeleteMapping("/suppliers/{id}")
    public ResponseEntity<Void> archiveSupplier(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                @RequestHeader(value = "X-User-Email", required = false) String actor,
                                                @PathVariable long id) {
        requireStaff(role); service.archiveSupplier(id, actor); return ResponseEntity.noContent().build();
    }

    @PatchMapping("/suppliers/{id}/restore")
    public Map<String, Object> restoreSupplier(@RequestHeader(value = "X-User-Role", required = false) String role,
                                               @RequestHeader(value = "X-User-Email", required = false) String actor,
                                               @PathVariable long id) {
        requireStaff(role); return service.restoreSupplier(id, actor);
    }

    @GetMapping("/suppliers/{supplierId}/products")
    public List<Map<String, Object>> supplierProducts(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                      @PathVariable long supplierId,
                                                      @RequestParam(defaultValue = "false") boolean includeInactive) {
        requireStaff(role); return service.supplierProducts(supplierId, includeInactive);
    }

    @PostMapping("/suppliers/{supplierId}/products")
    public ResponseEntity<Map<String, Object>> createSupplierProduct(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                                     @RequestHeader(value = "X-User-Email", required = false) String actor,
                                                                     @PathVariable long supplierId,
                                                                     @RequestBody Map<String, Object> request) {
        requireStaff(role); return ResponseEntity.status(HttpStatus.CREATED).body(service.createSupplierProduct(supplierId, request, actor));
    }

    @PutMapping("/suppliers/{supplierId}/products/{productId}")
    public Map<String, Object> updateSupplierProduct(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                     @RequestHeader(value = "X-User-Email", required = false) String actor,
                                                     @PathVariable long supplierId, @PathVariable long productId,
                                                     @RequestBody Map<String, Object> request) {
        requireStaff(role); return service.updateSupplierProduct(supplierId, productId, request, actor);
    }

    @DeleteMapping("/suppliers/{supplierId}/products/{productId}")
    public ResponseEntity<Void> archiveSupplierProduct(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                        @RequestHeader(value = "X-User-Email", required = false) String actor,
                                                        @PathVariable long supplierId, @PathVariable long productId) {
        requireStaff(role); service.archiveSupplierProduct(supplierId, productId, actor); return ResponseEntity.noContent().build();
    }

    @GetMapping("/purchase-orders")
    public List<Map<String, Object>> purchaseOrders(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                    @RequestParam(required = false) String status) {
        requireStaff(role); return service.purchaseOrders(status);
    }

    @GetMapping("/purchase-orders/{id}")
    public Map<String, Object> purchaseOrder(@RequestHeader(value = "X-User-Role", required = false) String role,
                                             @PathVariable long id) {
        requireStaff(role); return service.purchaseOrder(id);
    }

    @PostMapping("/purchase-orders")
    public ResponseEntity<Map<String, Object>> createPurchaseOrder(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                                   @RequestHeader(value = "X-User-Email", required = false) String actor,
                                                                   @RequestBody Map<String, Object> request) {
        requireStaff(role); return ResponseEntity.status(HttpStatus.CREATED).body(service.createPurchaseOrder(request, actor));
    }

    @PutMapping("/purchase-orders/{id}")
    public Map<String, Object> updatePurchaseOrder(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                   @RequestHeader(value = "X-User-Email", required = false) String actor,
                                                   @PathVariable long id, @RequestBody Map<String, Object> request) {
        requireStaff(role); return service.updatePurchaseOrder(id, request, actor);
    }

    @PostMapping("/purchase-orders/{id}/approve")
    public Map<String, Object> approvePurchaseOrder(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                    @RequestHeader(value = "X-User-Email", required = false) String actor,
                                                    @PathVariable long id) {
        requireStaff(role); return service.approvePurchaseOrder(id, actor);
    }

    @PostMapping("/purchase-orders/{id}/issue")
    public Map<String, Object> issuePurchaseOrder(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                  @RequestHeader(value = "X-User-Email", required = false) String actor,
                                                  @PathVariable long id) {
        requireStaff(role); return service.issuePurchaseOrder(id, actor);
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    public Map<String, Object> cancelPurchaseOrder(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                   @RequestHeader(value = "X-User-Email", required = false) String actor,
                                                   @PathVariable long id) {
        requireStaff(role); return service.cancelPurchaseOrder(id, actor);
    }

    @PostMapping("/purchase-orders/{id}/receive")
    public Map<String, Object> receivePurchaseOrder(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                    @RequestHeader(value = "X-User-Email", required = false) String actor,
                                                    @PathVariable long id, @RequestBody Map<String, Object> request) {
        requireStaff(role); return service.receivePurchaseOrder(id, request, actor);
    }

    @GetMapping("/supplier-invoices")
    public List<Map<String, Object>> supplierInvoices(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                      @RequestParam(required = false) String status) {
        requireStaff(role); return service.supplierInvoices(status);
    }

    @PostMapping("/purchase-orders/{id}/supplier-invoices")
    public ResponseEntity<Map<String, Object>> createSupplierInvoice(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                                     @RequestHeader(value = "X-User-Email", required = false) String actor,
                                                                     @PathVariable long id, @RequestBody Map<String, Object> request) {
        requireStaff(role); return ResponseEntity.status(HttpStatus.CREATED).body(service.createSupplierInvoice(id, request, actor));
    }

    @GetMapping("/supplier-invoices/{id}")
    public Map<String, Object> supplierInvoice(@RequestHeader(value = "X-User-Role", required = false) String role,
                                               @PathVariable long id) {
        requireStaff(role); return service.supplierInvoice(id);
    }

    @PostMapping("/supplier-invoices/{id}/approve")
    public Map<String, Object> approveSupplierInvoice(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                      @RequestHeader(value = "X-User-Email", required = false) String actor,
                                                      @PathVariable long id) {
        requireStaff(role); return service.approveSupplierInvoice(id, actor);
    }

    @GetMapping("/replenishment-suggestions")
    public List<Map<String, Object>> replenishmentSuggestions(@RequestHeader(value = "X-User-Role", required = false) String role) {
        requireStaff(role); return service.replenishmentSuggestions();
    }

    @PostMapping("/replenishment-suggestions/{productId}/draft-purchase-order")
    public ResponseEntity<Map<String, Object>> createDraftFromSuggestion(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                                         @RequestHeader(value = "X-User-Email", required = false) String actor,
                                                                         @PathVariable long productId,
                                                                         @RequestParam(required = false) Long supplierId) {
        requireStaff(role); return ResponseEntity.status(HttpStatus.CREATED).body(service.createDraftFromSuggestion(productId, supplierId, actor));
    }

    private void requireStaff(String role) {
        if (role == null || (!role.toUpperCase(Locale.ROOT).contains("ADMIN") && !role.toUpperCase(Locale.ROOT).contains("MANAGER"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager or Admin authorization required.");
        }
    }
}
