package com.ecommerce.product.storefront;

import com.ecommerce.product.pricing.PricingSupport;
import com.ecommerce.product.security.CatalogPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

import static com.ecommerce.product.pricing.PricingSupport.*;

/** Hero/promo/strip banners with desktop and mobile images, scheduling windows, priorities and optional campaign links. */
@Service
public class BannerService {
    public record BannerRequest(String name, String placement, Long desktopMediaId, Long mobileMediaId, String altText, String destinationUrl,
                                Long campaignId, OffsetDateTime startsAt, OffsetDateTime endsAt, Integer priority, Boolean active) {}

    private final JdbcTemplate jdbc;
    private final PricingSupport support;
    private final StorefrontService storefronts;

    public BannerService(JdbcTemplate jdbc, PricingSupport support, StorefrontService storefronts) { this.jdbc = jdbc; this.support = support; this.storefronts = storefronts; }

    public List<Map<String, Object>> list(CatalogPrincipal actor, long storefrontId) {
        storefronts.get(actor, storefrontId);
        return support.rows("SELECT * FROM storefront_banners WHERE tenant_id=? AND storefront_id=? ORDER BY priority, id", actor.tenantId(), storefrontId);
    }

    @Transactional
    public Map<String, Object> create(CatalogPrincipal actor, long storefrontId, BannerRequest r) {
        storefronts.get(actor, storefrontId);
        Object[] v = validate(actor, storefrontId, r);
        jdbc.update("INSERT INTO storefront_banners(tenant_id, storefront_id, name, placement, desktop_media_id, mobile_media_id, alt_text, destination_url, campaign_id, starts_at, ends_at, priority, active, created_by, updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                actor.tenantId(), storefrontId, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], actor.userId(), actor.userId());
        long id = jdbc.queryForObject("SELECT MAX(id) FROM storefront_banners WHERE tenant_id=? AND storefront_id=?", Long.class, actor.tenantId(), storefrontId);
        storefronts.audit(actor, storefrontId, "BANNER", id, "CREATE", String.valueOf(v[0]));
        return get(actor, id);
    }

    @Transactional
    public Map<String, Object> update(CatalogPrincipal actor, long id, BannerRequest r) {
        Map<String, Object> existing = get(actor, id);
        long storefrontId = ((Number) existing.get("storefrontId")).longValue();
        Object[] v = validate(actor, storefrontId, r);
        jdbc.update("UPDATE storefront_banners SET name=?, placement=?, desktop_media_id=?, mobile_media_id=?, alt_text=?, destination_url=?, campaign_id=?, starts_at=?, ends_at=?, priority=?, active=?, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], actor.userId(), id, actor.tenantId());
        storefronts.audit(actor, storefrontId, "BANNER", id, "UPDATE", String.valueOf(v[0]));
        return get(actor, id);
    }

    @Transactional
    public void delete(CatalogPrincipal actor, long id) {
        Map<String, Object> b = get(actor, id);
        jdbc.update("DELETE FROM storefront_banners WHERE id=? AND tenant_id=?", id, actor.tenantId());
        storefronts.audit(actor, ((Number) b.get("storefrontId")).longValue(), "BANNER", id, "DELETE", String.valueOf(b.get("name")));
    }

    @Transactional
    public Map<String, Object> reorder(CatalogPrincipal actor, long storefrontId, List<Long> orderedIds) {
        storefronts.get(actor, storefrontId);
        if (orderedIds == null || orderedIds.isEmpty() || new HashSet<>(orderedIds).size() != orderedIds.size()) throw bad("Provide each banner id once.");
        int n = 10;
        for (Long id : orderedIds) {
            int u = jdbc.update("UPDATE storefront_banners SET priority=?, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND storefront_id=?", n, actor.userId(), id, actor.tenantId(), storefrontId);
            if (u != 1) throw notFound("Banner " + id);
            n += 10;
        }
        storefronts.audit(actor, storefrontId, "BANNER", "order", "REORDER", orderedIds.toString());
        return Map.of("reordered", orderedIds.size());
    }

    public Map<String, Object> get(CatalogPrincipal actor, long id) {
        return support.one("SELECT * FROM storefront_banners WHERE id=? AND tenant_id=?", "Banner", id, actor.tenantId());
    }

    private Object[] validate(CatalogPrincipal actor, long storefrontId, BannerRequest r) {
        String name = text(r.name(), "name", 160);
        String placement = r.placement() == null ? "HERO" : r.placement();
        if (!Set.of("HERO", "PROMO", "STRIP").contains(placement)) throw bad("placement must be HERO, PROMO or STRIP.");
        if (r.desktopMediaId() == null) throw bad("A desktop image is required.");
        requireMedia(actor, storefrontId, r.desktopMediaId(), "BANNER_DESKTOP");
        if (r.mobileMediaId() != null) requireMedia(actor, storefrontId, r.mobileMediaId(), "BANNER_MOBILE");
        String alt = ContentValidator.plain(r.altText(), "altText", 255, true);
        String url = ContentValidator.safeUrl(r.destinationUrl(), "destinationUrl");
        if (r.campaignId() != null) {
            Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM promotion_campaigns WHERE id=? AND tenant_id=?", Integer.class, r.campaignId(), actor.tenantId());
            if (c == null || c == 0) throw bad("Campaign " + r.campaignId() + " does not exist in this tenant.");
        }
        if (r.startsAt() != null && r.endsAt() != null && !r.endsAt().isAfter(r.startsAt())) throw bad("endsAt must be after startsAt.");
        int priority = r.priority() == null ? 100 : r.priority();
        if (priority < 0 || priority > 10000) throw bad("priority must be 0-10000.");
        return new Object[]{name, placement, r.desktopMediaId(), r.mobileMediaId(), alt, url, r.campaignId(), r.startsAt(), r.endsAt(), priority, r.active() == null || r.active()};
    }

    private void requireMedia(CatalogPrincipal actor, long storefrontId, long mediaId, String kind) {
        List<String> k = jdbc.queryForList("SELECT kind FROM storefront_media WHERE id=? AND tenant_id=? AND storefront_id=?", String.class, mediaId, actor.tenantId(), storefrontId);
        if (k.isEmpty()) throw bad("Image " + mediaId + " does not exist in this storefront.");
        if (!k.get(0).equals(kind)) throw bad("Image " + mediaId + " is a " + k.get(0) + " image; a " + kind + " image is required here.");
    }
}
