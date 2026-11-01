package com.ecommerce.loyalty.controller;

import com.ecommerce.loyalty.dto.*;
import com.ecommerce.loyalty.service.LoyaltyService;
import com.ecommerce.loyalty.model.LoyaltyTier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/loyalty/admin")
@Tag(name = "Loyalty Admin", description = "Admin-only loyalty program management")
public class LoyaltyAdminController {

    @Autowired
    private LoyaltyService loyaltyService;

    private boolean isAdmin(HttpServletRequest request) {
        String role = request.getHeader("X-User-Role");
        return "ADMIN".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get aggregate loyalty program statistics")
    public ResponseEntity<LoyaltyAdminStatsDto> getStats(HttpServletRequest request) {
        if (!isAdmin(request)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(loyaltyService.getAdminStats());
    }

    @GetMapping("/accounts")
    @Operation(summary = "List all loyalty accounts")
    public ResponseEntity<List<LoyaltyAccountDto>> getAccounts(HttpServletRequest request) {
        if (!isAdmin(request)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(loyaltyService.getAllAccounts());
    }

    @GetMapping("/tiers")
    @Operation(summary = "List all tier rules")
    public ResponseEntity<List<LoyaltyTierRuleDto>> getTierRules(HttpServletRequest request) {
        if (!isAdmin(request)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(loyaltyService.getAllTierRules());
    }

    @PutMapping("/tiers/{tier}")
    @Operation(summary = "Update a built-in loyalty tier rule")
    public ResponseEntity<LoyaltyTierRuleDto> updateTierRule(@PathVariable LoyaltyTier tier,
            @RequestBody LoyaltyTierRuleDto body, HttpServletRequest request) {
        if (!isAdmin(request)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(loyaltyService.updateTierRule(tier, body));
    }
}
