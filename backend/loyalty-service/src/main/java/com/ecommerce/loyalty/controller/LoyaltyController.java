package com.ecommerce.loyalty.controller;

import com.ecommerce.loyalty.dto.*;
import com.ecommerce.loyalty.service.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/loyalty")
@Tag(name = "Loyalty", description = "Customer loyalty points and tier management")
public class LoyaltyController {

    @Autowired
    private LoyaltyService loyaltyService;

    @GetMapping("/{userId}")
    @Operation(summary = "Get loyalty profile for a user")
    public ResponseEntity<LoyaltyAccountDto> getProfile(@PathVariable Long userId,
                                                         HttpServletRequest request) {
        // Allow self-access or admin
        String requestingUserId = request.getHeader("X-User-Id");
        String role = request.getHeader("X-User-Role");
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role);
        if (!isAdmin && (requestingUserId == null || !requestingUserId.equals(userId.toString()))) {
            return ResponseEntity.status(403).build();
        }
        try {
            return ResponseEntity.ok(loyaltyService.getOrCreateProfile(userId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{userId}/earn")
    @Operation(summary = "Earn points for a completed order")
    public ResponseEntity<LoyaltyAccountDto> earnPoints(@PathVariable Long userId,
                                                         @RequestBody EarnPointsRequest request,
                                                         HttpServletRequest httpRequest) {
        if (!isStaff(httpRequest.getHeader("X-User-Role"))) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(loyaltyService.earnPoints(userId, request));
    }

    @PostMapping("/{userId}/redeem")
    @Operation(summary = "Redeem points for a discount")
    public ResponseEntity<RedeemPointsResponse> redeemPoints(@PathVariable Long userId,
                                                              @RequestBody RedeemPointsRequest request,
                                                              HttpServletRequest httpRequest) {
        if (!ownsAccount(userId, httpRequest)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(loyaltyService.redeemPoints(userId, request));
    }

    @GetMapping("/{userId}/transactions")
    @Operation(summary = "Get transaction history for a user")
    public ResponseEntity<List<LoyaltyTransactionDto>> getTransactions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        String requestingUserId = request.getHeader("X-User-Id");
        String role = request.getHeader("X-User-Role");
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role);
        if (!isAdmin && (requestingUserId == null || !requestingUserId.equals(userId.toString()))) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(loyaltyService.getTransactions(userId, page, size));
    }

    @PostMapping("/{userId}/bonus")
    @Operation(summary = "Award bonus points (admin only)")
    public ResponseEntity<LoyaltyAccountDto> awardBonus(@PathVariable Long userId,
                                                         @RequestBody BonusPointsRequest request,
                                                         HttpServletRequest httpRequest) {
        if (!isStaff(httpRequest.getHeader("X-User-Role"))) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(loyaltyService.awardBonus(userId, request));
    }

    private boolean ownsAccount(Long userId, HttpServletRequest request) {
        return isStaff(request.getHeader("X-User-Role")) ||
                userId.toString().equals(request.getHeader("X-User-Id"));
    }

    private boolean isStaff(String role) {
        return "ADMIN".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role);
    }
}
