package com.ecommerce.loyalty.controller;

import com.ecommerce.loyalty.dto.*;
import com.ecommerce.loyalty.security.LoyaltyPrincipal;
import com.ecommerce.loyalty.service.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/loyalty")
@Tag(name = "Loyalty", description = "Customer loyalty points and tier management")
public class LoyaltyController {

    @Autowired
    private LoyaltyService loyaltyService;

    @GetMapping("/{userId}")
    @Operation(summary = "Get loyalty profile for a user")
    public ResponseEntity<LoyaltyAccountDto> getProfile(@PathVariable Long userId) {
        LoyaltyPrincipal principal = requirePrincipal();
        if (principal == null) return ResponseEntity.status(401).build();
        if (!canRead(principal, userId)) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(loyaltyService.getOrCreateProfile(userId, principal.tenantId()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{userId}/earn")
    @Operation(summary = "Earn points for a completed order")
    public ResponseEntity<LoyaltyAccountDto> earnPoints(@PathVariable Long userId,
                                                         @RequestBody EarnPointsRequest request) {
        LoyaltyPrincipal principal = requirePrincipal();
        if (principal == null) return ResponseEntity.status(401).build();
        if (!isStaff(principal)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(loyaltyService.earnPoints(userId, principal.tenantId(), request));
    }

    @PostMapping("/{userId}/redeem")
    @Operation(summary = "Redeem points for a discount")
    public ResponseEntity<RedeemPointsResponse> redeemPoints(@PathVariable Long userId,
                                                              @RequestBody RedeemPointsRequest request) {
        LoyaltyPrincipal principal = requirePrincipal();
        if (principal == null) return ResponseEntity.status(401).build();
        if (!ownsAccount(principal, userId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(loyaltyService.redeemPoints(userId, principal.tenantId(), request));
    }

    @GetMapping("/{userId}/transactions")
    @Operation(summary = "Get transaction history for a user")
    public ResponseEntity<List<LoyaltyTransactionDto>> getTransactions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        LoyaltyPrincipal principal = requirePrincipal();
        if (principal == null) return ResponseEntity.status(401).build();
        if (!canRead(principal, userId)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(loyaltyService.getTransactions(userId, principal.tenantId(), page, size));
    }

    @PostMapping("/{userId}/bonus")
    @Operation(summary = "Award bonus points (admin only)")
    public ResponseEntity<LoyaltyAccountDto> awardBonus(@PathVariable Long userId,
                                                         @RequestBody BonusPointsRequest request) {
        LoyaltyPrincipal principal = requirePrincipal();
        if (principal == null) return ResponseEntity.status(401).build();
        if (!isStaff(principal)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(loyaltyService.awardBonus(userId, principal.tenantId(), request));
    }

    private LoyaltyPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoyaltyPrincipal p) return p;
        return null;
    }

    private boolean canRead(LoyaltyPrincipal p, Long targetUserId) {
        return isStaff(p) || p.userId() == targetUserId;
    }

    private boolean ownsAccount(LoyaltyPrincipal p, Long targetUserId) {
        return isStaff(p) || p.userId() == targetUserId;
    }

    private boolean isStaff(LoyaltyPrincipal p) {
        String role = p.tenantRole();
        return "ADMIN".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role)
                || "SUPER_ADMIN".equalsIgnoreCase(role);
    }
}
