package com.ecommerce.product.pricing;

import com.ecommerce.product.pricing.CampaignService.CampaignRequest;
import com.ecommerce.product.pricing.CouponAdminService.CouponRequest;
import com.ecommerce.product.pricing.PriceService.PriceChange;
import com.ecommerce.product.security.CatalogPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Pricing & Promotions administration for the E-commerce Admin application. Authentication and the current application grant are
 * enforced by CatalogTenantAuthenticationFilter; every endpoint additionally checks a fine-grained permission claim.
 */
@RestController
@RequestMapping("/api/products/admin/pricing")
public class PricingAdminController {
    private final PriceService prices;
    private final CampaignService campaigns;
    private final CouponAdminService coupons;
    private final QuoteService quotes;
    private final PricingSettingsService settings;

    public PricingAdminController(PriceService prices, CampaignService campaigns, CouponAdminService coupons, QuoteService quotes,
                                  PricingSettingsService settings) {
        this.prices = prices; this.campaigns = campaigns; this.coupons = coupons; this.quotes = quotes; this.settings = settings;
    }

    private static void require(CatalogPrincipal actor, String... anyOf) {
        for (String p : anyOf) if (actor.hasPermission(p)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission " + String.join(" or ", anyOf) + ".");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }

    // ---- dashboard & settings ----------------------------------------------------------------------------------
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@AuthenticationPrincipal CatalogPrincipal a) { require(a, "pricing:read", "promotion:read"); return prices.dashboard(a); }

    @GetMapping("/settings")
    public Map<String, Object> getSettings(@AuthenticationPrincipal CatalogPrincipal a, @RequestParam(required = false) String storeId) {
        require(a, "pricing:read", "promotion:read"); return settings.view(a.tenantId(), storeId);
    }

    @PutMapping("/settings")
    public Map<String, Object> putSettings(@AuthenticationPrincipal CatalogPrincipal a, @RequestParam(required = false) String storeId,
                                           @RequestBody Map<String, Object> body) {
        require(a, "pricing:publish"); return settings.upsert(a, storeId, body);
    }

    // ---- prices ------------------------------------------------------------------------------------------------
    @GetMapping("/prices")
    public List<Map<String, Object>> listPrices(@AuthenticationPrincipal CatalogPrincipal a, @RequestParam(required = false) Long productId,
                                                @RequestParam(required = false) String storeId, @RequestParam(defaultValue = "200") int limit) {
        require(a, "pricing:read"); return prices.list(a, productId, storeId, limit);
    }

    @PostMapping("/prices")
    public ResponseEntity<Map<String, Object>> createPrice(@AuthenticationPrincipal CatalogPrincipal a, @RequestBody PriceChange body) {
        require(a, "pricing:create");
        return ResponseEntity.status(HttpStatus.CREATED).body(prices.setPrice(a, body, "MANUAL", true));
    }

    @PutMapping("/prices")
    public Map<String, Object> updatePrice(@AuthenticationPrincipal CatalogPrincipal a, @RequestBody PriceChange body) {
        require(a, "pricing:update"); return prices.setPrice(a, body, "MANUAL", false);
    }

    public record BulkRequest(List<Long> productIds, String mode, BigDecimal value, String currency, String storeId, String reason, Boolean dryRun) {}

    @PostMapping("/prices/bulk")
    public List<Map<String, Object>> bulk(@AuthenticationPrincipal CatalogPrincipal a, @RequestBody BulkRequest r) {
        require(a, "pricing:update");
        return prices.bulkAdjust(a, r.productIds(), r.mode(), r.value(), r.currency(), r.storeId(), r.reason(), Boolean.TRUE.equals(r.dryRun()));
    }

    @GetMapping("/prices/history")
    public List<Map<String, Object>> history(@AuthenticationPrincipal CatalogPrincipal a, @RequestParam(required = false) Long productId,
                                             @RequestParam(defaultValue = "200") int limit) {
        require(a, "pricing:read"); return prices.history(a, productId, limit);
    }

    @PostMapping("/prices/history/{id}/rollback")
    public Map<String, Object> rollback(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id) {
        require(a, "pricing:publish"); return prices.rollback(a, id);
    }

    @GetMapping(value = "/prices/export", produces = "text/csv")
    public ResponseEntity<String> export(@AuthenticationPrincipal CatalogPrincipal a) {
        require(a, "pricing:export");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"prices.csv\"").body(prices.exportCsv(a));
    }

    @PostMapping(value = "/prices/import", consumes = {"text/csv", "text/plain"})
    public Map<String, Object> importCsv(@AuthenticationPrincipal CatalogPrincipal a, @RequestBody String csv) {
        require(a, "pricing:create"); require(a, "pricing:update");
        return prices.importCsv(a, csv);
    }

    // ---- scheduled prices --------------------------------------------------------------------------------------
    public record ScheduleRequest(Long productId, Long variantId, String currency, BigDecimal basePrice, BigDecimal salePrice, String storeId,
                                  String reason, OffsetDateTime effectiveFrom) {}

    @GetMapping("/scheduled")
    public List<Map<String, Object>> scheduled(@AuthenticationPrincipal CatalogPrincipal a) { require(a, "pricing:read"); return prices.listScheduled(a); }

    @PostMapping("/scheduled")
    public ResponseEntity<Map<String, Object>> schedule(@AuthenticationPrincipal CatalogPrincipal a, @RequestBody ScheduleRequest r) {
        require(a, "pricing:create");
        return ResponseEntity.status(HttpStatus.CREATED).body(prices.schedule(a, new PriceChange(r.productId(), r.variantId(), r.currency(),
                r.basePrice(), r.salePrice(), null, r.storeId(), r.reason(), null), r.effectiveFrom()));
    }

    public record Decision(Boolean approve, String note) {}

    @PostMapping("/scheduled/{id}/decision")
    public Map<String, Object> decideScheduled(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id, @RequestBody Decision d) {
        require(a, "pricing:approve"); return prices.decideScheduled(a, id, Boolean.TRUE.equals(d.approve()), d.note());
    }

    @PostMapping("/scheduled/{id}/cancel")
    public Map<String, Object> cancelScheduled(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id) {
        require(a, "pricing:update"); return prices.cancelScheduled(a, id);
    }

    // ---- campaigns ---------------------------------------------------------------------------------------------
    @GetMapping("/campaigns")
    public List<Map<String, Object>> campaigns(@AuthenticationPrincipal CatalogPrincipal a, @RequestParam(required = false) String type,
                                               @RequestParam(required = false) String status, @RequestParam(required = false) Boolean automatic,
                                               @RequestParam(required = false) Boolean segmentOnly) {
        require(a, "promotion:read"); return campaigns.list(a, type, status, automatic, segmentOnly);
    }

    @GetMapping("/campaigns/{id}")
    public Map<String, Object> campaign(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id) { require(a, "promotion:read"); return campaigns.get(a, id); }

    @PostMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> createCampaign(@AuthenticationPrincipal CatalogPrincipal a, @RequestBody CampaignRequest r) {
        require(a, "promotion:create"); return ResponseEntity.status(HttpStatus.CREATED).body(campaigns.create(a, r));
    }

    @PutMapping("/campaigns/{id}")
    public Map<String, Object> updateCampaign(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id, @RequestBody CampaignRequest r) {
        require(a, "promotion:update"); return campaigns.update(a, id, r);
    }

    @PostMapping("/campaigns/{id}/submit")
    public Map<String, Object> submit(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id) { require(a, "promotion:update"); return campaigns.submit(a, id); }

    @PostMapping("/campaigns/{id}/decision")
    public Map<String, Object> decide(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id, @RequestBody Decision d) {
        require(a, "promotion:approve"); return campaigns.decide(a, id, Boolean.TRUE.equals(d.approve()), d.note());
    }

    @PostMapping("/campaigns/{id}/publish")
    public Map<String, Object> publish(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id) { require(a, "pricing:publish"); return campaigns.publish(a, id); }

    @PostMapping("/campaigns/{id}/pause")
    public Map<String, Object> pause(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id) { require(a, "promotion:update"); return campaigns.pause(a, id); }

    @PostMapping("/campaigns/{id}/resume")
    public Map<String, Object> resume(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id) { require(a, "promotion:update"); return campaigns.resume(a, id); }

    @PostMapping("/campaigns/{id}/cancel")
    public Map<String, Object> cancel(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id, @RequestBody(required = false) Decision d) {
        require(a, "promotion:update"); return campaigns.cancel(a, id, d == null ? null : d.note());
    }

    @GetMapping("/campaigns/{id}/conflicts")
    public List<Map<String, Object>> conflicts(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id) { require(a, "promotion:read"); return campaigns.conflicts(a, id); }

    @GetMapping("/calendar")
    public List<Map<String, Object>> calendar(@AuthenticationPrincipal CatalogPrincipal a,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        require(a, "promotion:read"); return campaigns.calendar(a, from, to);
    }

    @GetMapping("/performance")
    public List<Map<String, Object>> performance(@AuthenticationPrincipal CatalogPrincipal a) { require(a, "promotion:read"); return campaigns.performance(a); }

    @GetMapping("/approvals")
    public List<Map<String, Object>> approvals(@AuthenticationPrincipal CatalogPrincipal a, @RequestParam(required = false) String status) {
        require(a, "promotion:read", "pricing:approve"); return campaigns.approvals(a, status);
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit(@AuthenticationPrincipal CatalogPrincipal a, @RequestParam(required = false) String entityType,
                                           @RequestParam(required = false) String entityId) {
        require(a, "pricing:audit"); return campaigns.audit(a, entityType, entityId);
    }

    // ---- coupons -----------------------------------------------------------------------------------------------
    @GetMapping("/coupons")
    public List<Map<String, Object>> listCoupons(@AuthenticationPrincipal CatalogPrincipal a, @RequestParam(required = false) Long campaignId,
                                                 @RequestParam(required = false) String status, @RequestParam(required = false) String q) {
        require(a, "coupon:manage", "promotion:read"); return coupons.list(a, campaignId, status, q);
    }

    @PostMapping("/coupons")
    public ResponseEntity<List<Map<String, Object>>> createCoupons(@AuthenticationPrincipal CatalogPrincipal a, @RequestBody CouponRequest r) {
        require(a, "coupon:manage"); return ResponseEntity.status(HttpStatus.CREATED).body(coupons.create(a, r));
    }

    @PostMapping("/coupons/{id}/{action}")
    public Map<String, Object> couponAction(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id, @PathVariable String action) {
        require(a, "coupon:manage"); return coupons.transition(a, id, action);
    }

    @GetMapping("/redemptions")
    public List<Map<String, Object>> redemptions(@AuthenticationPrincipal CatalogPrincipal a, @RequestParam(required = false) Long couponId,
                                                 @RequestParam(required = false) Long campaignId) {
        require(a, "coupon:manage", "promotion:read"); return coupons.redemptions(a, couponId, campaignId);
    }

    /** Preview the exact checkout calculation without consuming anything. */
    @PostMapping("/quote-preview")
    public Map<String, Object> preview(@AuthenticationPrincipal CatalogPrincipal a, @RequestBody QuoteService.QuoteRequest r) {
        require(a, "promotion:read"); return quotes.quote(a.tenantId(), r);
    }
}
