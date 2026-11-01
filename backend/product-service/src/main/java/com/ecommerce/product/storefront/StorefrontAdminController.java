package com.ecommerce.product.storefront;

import com.ecommerce.product.security.CatalogPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Storefront Management for the E-commerce Admin application. The tenant always comes from the verified JWT (never a header or body
 * field); every endpoint additionally enforces a fine-grained permission. Gateway + CatalogTenantAuthenticationFilter run first.
 */
@RestController
@RequestMapping("/api/products/admin/storefront")
public class StorefrontAdminController {
    private final StorefrontService storefronts;
    private final MediaService media;
    private final BannerService banners;
    private final DomainService domains;
    private final ThemePresetService presets;
    private final PublicStorefrontService publicService;

    public StorefrontAdminController(StorefrontService storefronts, MediaService media, BannerService banners, DomainService domains,
                                     ThemePresetService presets, PublicStorefrontService publicService) {
        this.storefronts = storefronts; this.media = media; this.banners = banners; this.domains = domains; this.presets = presets; this.publicService = publicService;
    }

    private static void require(CatalogPrincipal a, String permission) {
        if (!a.hasPermission(permission)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission " + permission + ".");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }

    // ---- storefronts ---------------------------------------------------------------------------------------------
    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal CatalogPrincipal a) { require(a, "storefront:read"); return storefronts.list(a); }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@AuthenticationPrincipal CatalogPrincipal a, @RequestBody StorefrontService.StorefrontRequest r) {
        require(a, "storefront:manage"); return ResponseEntity.status(HttpStatus.CREATED).body(storefronts.create(a, r));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id) { require(a, "storefront:read"); return storefronts.get(a, id); }

    public record NameRequest(String name) {}

    @PutMapping("/{id}")
    public Map<String, Object> rename(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id, @RequestBody NameRequest r) { require(a, "storefront:manage"); return storefronts.updateName(a, id, r.name()); }

    @GetMapping("/{id}/dashboard")
    public Map<String, Object> dashboard(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id) { require(a, "storefront:read"); return storefronts.dashboard(a, id); }

    @GetMapping("/{id}/audit")
    public List<Map<String, Object>> audit(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id, @RequestParam(defaultValue = "200") int limit) {
        require(a, "storefront:audit"); storefronts.get(a, id); return storefronts.auditLog(a, id, limit);
    }

    // ---- documents & versions ------------------------------------------------------------------------------------
    @GetMapping("/{id}/documents")
    public List<Map<String, Object>> documents(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id) { require(a, "storefront:read"); return storefronts.documents(a, id); }

    public record LandingRequest(String slug, String title, JsonNode content) {}

    @PostMapping("/{id}/pages")
    public ResponseEntity<Map<String, Object>> createLanding(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id, @RequestBody LandingRequest r) {
        require(a, "storefront:edit"); return ResponseEntity.status(HttpStatus.CREATED).body(storefronts.createLanding(a, id, r.slug(), r.title(), r.content()));
    }

    public record TitleRequest(String title) {}

    @PutMapping("/documents/{docId}")
    public Map<String, Object> renameDoc(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long docId, @RequestBody TitleRequest r) { require(a, "storefront:edit"); return storefronts.rename(a, docId, r.title()); }

    @GetMapping("/documents/{docId}/versions")
    public List<Map<String, Object>> versions(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long docId) { require(a, "storefront:read"); return storefronts.versions(a, docId); }

    @GetMapping("/documents/{docId}/versions/{vid}")
    public Map<String, Object> version(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long docId, @PathVariable long vid) { require(a, "storefront:read"); return storefronts.version(a, docId, vid); }

    public record DraftRequest(JsonNode content, String note) {}

    @PutMapping("/documents/{docId}/draft")
    public Map<String, Object> saveDraft(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long docId, @RequestBody DraftRequest r) {
        require(a, "storefront:edit"); return storefronts.saveDraft(a, docId, r.content(), r.note());
    }

    public record NoteRequest(String note) {}
    public record DecisionRequest(Boolean approve, String note) {}
    public record PublishRequest(OffsetDateTime at, String note) {}

    @PostMapping("/documents/{docId}/versions/{vid}/submit")
    public Map<String, Object> submit(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long docId, @PathVariable long vid, @RequestBody(required = false) NoteRequest r) {
        require(a, "storefront:edit"); return storefronts.submit(a, docId, vid, r == null ? null : r.note());
    }

    @PostMapping("/documents/{docId}/versions/{vid}/decision")
    public Map<String, Object> decide(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long docId, @PathVariable long vid, @RequestBody DecisionRequest r) {
        require(a, "storefront:approve"); return storefronts.decide(a, docId, vid, Boolean.TRUE.equals(r.approve()), r.note());
    }

    @PostMapping("/documents/{docId}/versions/{vid}/withdraw")
    public Map<String, Object> withdraw(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long docId, @PathVariable long vid, @RequestBody(required = false) NoteRequest r) {
        require(a, "storefront:edit"); return storefronts.withdraw(a, docId, vid, r == null ? null : r.note());
    }

    @PostMapping("/documents/{docId}/versions/{vid}/publish")
    public Map<String, Object> publish(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long docId, @PathVariable long vid, @RequestBody(required = false) PublishRequest r) {
        require(a, "storefront:publish"); return storefronts.publish(a, docId, vid, r == null ? null : r.at(), r == null ? null : r.note());
    }

    @PostMapping("/documents/{docId}/versions/{vid}/cancel-schedule")
    public Map<String, Object> cancelSchedule(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long docId, @PathVariable long vid) { require(a, "storefront:publish"); return storefronts.cancelSchedule(a, docId, vid); }

    @PostMapping("/documents/{docId}/versions/{vid}/rollback")
    public Map<String, Object> rollback(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long docId, @PathVariable long vid, @RequestBody(required = false) NoteRequest r) {
        require(a, "storefront:publish"); return storefronts.rollback(a, docId, vid, r == null ? null : r.note());
    }

    @PostMapping("/documents/{docId}/unpublish")
    public Map<String, Object> unpublish(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long docId) { require(a, "storefront:publish"); return storefronts.unpublish(a, docId); }

    @GetMapping("/documents/{docId}/compare")
    public Map<String, Object> compare(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long docId, @RequestParam long from, @RequestParam long to) {
        require(a, "storefront:read"); return storefronts.compare(a, docId, from, to);
    }

    @GetMapping("/documents/{docId}/preview")
    public Map<String, Object> preview(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long docId, @RequestParam(required = false) Long versionId) {
        require(a, "storefront:read"); return publicService.preview(a, docId, versionId);
    }

    // ---- media ---------------------------------------------------------------------------------------------------
    @GetMapping("/{id}/media")
    public List<Map<String, Object>> listMedia(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id, @RequestParam(required = false) String kind) { require(a, "storefront:read"); return media.list(a, id, kind); }

    @PostMapping(value = "/{id}/media", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> upload(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id, @RequestParam("file") MultipartFile file,
                                                      @RequestParam String kind, @RequestParam String altText) throws IOException {
        require(a, "storefront:media");
        return ResponseEntity.status(HttpStatus.CREATED).body(media.upload(a, id, kind, altText, file.getOriginalFilename(), file.getBytes()));
    }

    @DeleteMapping("/media/{mediaId}")
    public ResponseEntity<Void> deleteMedia(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long mediaId) { require(a, "storefront:media"); media.delete(a, mediaId); return ResponseEntity.noContent().build(); }

    // ---- banners -------------------------------------------------------------------------------------------------
    @GetMapping("/{id}/banners")
    public List<Map<String, Object>> listBanners(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id) { require(a, "storefront:read"); return banners.list(a, id); }

    @PostMapping("/{id}/banners")
    public ResponseEntity<Map<String, Object>> createBanner(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id, @RequestBody BannerService.BannerRequest r) {
        require(a, "storefront:edit"); return ResponseEntity.status(HttpStatus.CREATED).body(banners.create(a, id, r));
    }

    @PutMapping("/banners/{bannerId}")
    public Map<String, Object> updateBanner(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long bannerId, @RequestBody BannerService.BannerRequest r) { require(a, "storefront:edit"); return banners.update(a, bannerId, r); }

    @DeleteMapping("/banners/{bannerId}")
    public ResponseEntity<Void> deleteBanner(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long bannerId) { require(a, "storefront:edit"); banners.delete(a, bannerId); return ResponseEntity.noContent().build(); }

    public record ReorderRequest(List<Long> ids) {}

    @PostMapping("/{id}/banners/reorder")
    public Map<String, Object> reorderBanners(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id, @RequestBody ReorderRequest r) { require(a, "storefront:edit"); return banners.reorder(a, id, r.ids()); }

    // ---- domains -------------------------------------------------------------------------------------------------
    public record DomainRequest(String hostname) {}

    @GetMapping("/{id}/domains")
    public List<Map<String, Object>> listDomains(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id) { require(a, "storefront:read"); return domains.list(a, id); }

    @PostMapping("/{id}/domains")
    public ResponseEntity<Map<String, Object>> addDomain(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id, @RequestBody DomainRequest r) {
        require(a, "storefront:domain"); return ResponseEntity.status(HttpStatus.CREATED).body(domains.add(a, id, r.hostname()));
    }

    @PostMapping("/domains/{domainId}/verify")
    public Map<String, Object> verifyDomain(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long domainId) { require(a, "storefront:domain"); return domains.verify(a, domainId); }

    @PostMapping("/domains/{domainId}/check-ssl")
    public Map<String, Object> checkSsl(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long domainId) { require(a, "storefront:domain"); return domains.checkSsl(a, domainId); }

    @PostMapping("/domains/{domainId}/primary")
    public Map<String, Object> primary(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long domainId) { require(a, "storefront:domain"); return domains.makePrimary(a, domainId); }

    @DeleteMapping("/domains/{domainId}")
    public ResponseEntity<Void> removeDomain(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long domainId) { require(a, "storefront:domain"); domains.remove(a, domainId); return ResponseEntity.noContent().build(); }

    // ---- theme presets -------------------------------------------------------------------------------------------
    public record PresetRequest(String name, JsonNode theme) {}

    @GetMapping("/theme-presets")
    public List<Map<String, Object>> listPresets(@AuthenticationPrincipal CatalogPrincipal a) { require(a, "storefront:read"); return presets.list(a); }

    @PostMapping("/{id}/theme-presets")
    public ResponseEntity<Map<String, Object>> savePreset(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long id, @RequestBody PresetRequest r) {
        require(a, "storefront:edit"); return ResponseEntity.status(HttpStatus.CREATED).body(presets.save(a, id, r.name(), r.theme()));
    }

    @DeleteMapping("/theme-presets/{presetId}")
    public ResponseEntity<Void> deletePreset(@AuthenticationPrincipal CatalogPrincipal a, @PathVariable long presetId) { require(a, "storefront:edit"); presets.delete(a, presetId); return ResponseEntity.noContent().build(); }
}
