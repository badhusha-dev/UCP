package com.ecommerce.product.storefront;

import com.ecommerce.product.pricing.PricingSupport;
import com.ecommerce.product.security.CatalogPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.ecommerce.product.pricing.PricingSupport.notFound;

/**
 * Read-only storefront delivery. The tenant is derived from the storefront slug or a VERIFIED domain in our own tables, never from a
 * caller-supplied tenant id, and only PUBLISHED snapshots are ever returned. Anything else answers 404 so drafts cannot be probed.
 */
@Service
public class PublicStorefrontService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final StorefrontRenderer renderer;
    private final StorefrontService storefronts;

    @Value("${storefront.base-domain:storefront.localhost}") private String baseDomain;

    public PublicStorefrontService(JdbcTemplate jdbc, ObjectMapper mapper, StorefrontRenderer renderer, StorefrontService storefronts) {
        this.jdbc = jdbc; this.mapper = mapper; this.renderer = renderer; this.storefronts = storefronts;
    }

    Map<String, Object> bySlug(String slug) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM storefronts WHERE slug=? AND status='ACTIVE'", slug == null ? "" : slug.toLowerCase(Locale.ROOT));
        if (rows.isEmpty()) throw notFound("Storefront");
        return PricingSupport.camel(rows.get(0));
    }

    /** The tenant an ACTIVE storefront belongs to; 404 for an unknown or inactive slug, so a shopper never chooses the tenant. */
    public UUID tenantOf(String slug) { return tenant(bySlug(slug == null ? null : slug.trim())); }

    /** A VERIFIED custom domain, or the platform address {@code <slug>.<base-domain>} that {@link #baseUrl} advertises. */
    public Map<String, Object> resolveHost(String host) {
        String h = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT s.slug, d.hostname, d.is_primary FROM storefront_domains d JOIN storefronts s ON s.id=d.storefront_id WHERE d.hostname=? AND d.verification_status='VERIFIED' AND s.status='ACTIVE'", h);
        if (!rows.isEmpty()) return Map.of("slug", rows.get(0).get("slug"), "hostname", rows.get(0).get("hostname"), "primary", Boolean.TRUE.equals(rows.get(0).get("is_primary")));
        String suffix = "." + baseDomain.toLowerCase(Locale.ROOT);
        String label = h.endsWith(suffix) ? h.substring(0, h.length() - suffix.length()) : "";
        if (!label.isEmpty() && !label.contains(".")) {
            List<String> slugs = jdbc.queryForList("SELECT slug FROM storefronts WHERE slug=? AND status='ACTIVE'", String.class, label);
            if (!slugs.isEmpty()) return Map.of("slug", slugs.get(0), "hostname", h, "primary", false);
        }
        throw notFound("Storefront");
    }

    private Optional<JsonNode> published(Map<String, Object> sf, String type, String slug) {
        List<String> c = jdbc.queryForList("SELECT v.content FROM storefront_documents d JOIN storefront_versions v ON v.id=d.published_version_id WHERE d.tenant_id=? AND d.storefront_id=? AND d.doc_type=? AND d.slug=? AND v.status='PUBLISHED'",
                String.class, sf.get("tenantId"), sf.get("id"), type, slug);
        return c.isEmpty() ? Optional.empty() : Optional.of(storefronts.parse(c.get(0)));
    }

    private UUID tenant(Map<String, Object> sf) { return (UUID) sf.get("tenantId"); }

    /** Site-wide configuration: theme, navigation, display settings and identity. Unpublished parts are simply absent. */
    public Map<String, Object> site(String slug) {
        Map<String, Object> sf = bySlug(slug);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", sf.get("name")); out.put("slug", sf.get("slug")); out.put("type", sf.get("storefrontType"));
        out.put("defaultLocale", sf.get("defaultLocale")); out.put("locales", Arrays.asList(String.valueOf(sf.get("supportedLocales")).split(",")));
        out.put("baseUrl", baseUrl(sf));
        for (String[] d : new String[][]{{"theme", "THEME"}, {"navigation", "NAVIGATION"}, {"settings", "SETTINGS"}}) {
            out.put(d[0], published(sf, d[1], "").map(c -> (Object) renderer.render(tenant(sf), sf, c)).orElse(null));
        }
        out.put("structuredData", structuredData(sf, out));
        return out;
    }

    public Map<String, Object> page(String slug, String docType, String pageSlug) {
        Map<String, Object> sf = bySlug(slug);
        JsonNode c = published(sf, docType, pageSlug).orElseThrow(() -> notFound("Page"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("slug", pageSlug); out.put("content", renderer.render(tenant(sf), sf, c));
        out.put("title", jdbc.queryForObject("SELECT title FROM storefront_documents WHERE tenant_id=? AND storefront_id=? AND doc_type=? AND slug=?", String.class, sf.get("tenantId"), sf.get("id"), docType, pageSlug));
        return out;
    }

    public List<Map<String, Object>> banners(String slug) {
        Map<String, Object> sf = bySlug(slug);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT b.id, b.name, b.placement, b.alt_text, b.destination_url, b.priority, dm.public_id AS desktop_id, mm.public_id AS mobile_id FROM storefront_banners b "
                + "JOIN storefront_media dm ON dm.id=b.desktop_media_id LEFT JOIN storefront_media mm ON mm.id=b.mobile_media_id "
                + "WHERE b.tenant_id=? AND b.storefront_id=? AND b.active=TRUE AND (b.starts_at IS NULL OR b.starts_at<=CURRENT_TIMESTAMP) AND (b.ends_at IS NULL OR b.ends_at>CURRENT_TIMESTAMP) ORDER BY b.priority, b.id",
                sf.get("tenantId"), sf.get("id"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id")); m.put("name", r.get("name")); m.put("placement", r.get("placement")); m.put("altText", r.get("alt_text")); m.put("destinationUrl", r.get("destination_url"));
            m.put("desktopImageUrl", StorefrontRenderer.MEDIA_PATH + r.get("desktop_id"));
            m.put("mobileImageUrl", r.get("mobile_id") == null ? null : StorefrontRenderer.MEDIA_PATH + r.get("mobile_id"));
            out.add(m);
        }
        return out;
    }

    String baseUrl(Map<String, Object> sf) {
        List<String> primary = jdbc.queryForList("SELECT hostname FROM storefront_domains WHERE tenant_id=? AND storefront_id=? AND verification_status='VERIFIED' AND is_primary=TRUE", String.class, sf.get("tenantId"), sf.get("id"));
        return "https://" + (primary.isEmpty() ? sf.get("slug") + "." + baseDomain : primary.get(0));
    }

    public String sitemap(String slug) {
        Map<String, Object> sf = bySlug(slug);
        String base = baseUrl(sf);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        if (published(sf, "HOMEPAGE", "").filter(this::indexable).isPresent()) xml.append("  <url><loc>").append(esc(base + "/")).append("</loc></url>\n");
        for (Map<String, Object> r : jdbc.queryForList("SELECT d.slug, v.content, v.published_at FROM storefront_documents d JOIN storefront_versions v ON v.id=d.published_version_id WHERE d.tenant_id=? AND d.storefront_id=? AND d.doc_type='LANDING' AND v.status='PUBLISHED' ORDER BY d.slug",
                sf.get("tenantId"), sf.get("id"))) {
            if (indexable(storefronts.parse((String) r.get("content")))) xml.append("  <url><loc>").append(esc(base + "/p/" + r.get("slug"))).append("</loc></url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }

    private boolean indexable(JsonNode content) { return !content.path("seo").path("robots").asText("INDEX_FOLLOW").startsWith("NOINDEX"); }

    public String robots(String slug) {
        Map<String, Object> sf = bySlug(slug);
        return "User-agent: *\nAllow: /\nDisallow: /cart\nDisallow: /checkout\nDisallow: /account\nSitemap: " + baseUrl(sf) + "/sitemap.xml\n";
    }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;"); }

    /** schema.org WebSite/Organization built as data (serialised by Jackson), never concatenated into markup. */
    private JsonNode structuredData(Map<String, Object> sf, Map<String, Object> site) {
        ObjectNode ld = mapper.createObjectNode();
        ld.put("@context", "https://schema.org");
        ld.put("@type", "RESTAURANT".equals(sf.get("storefrontType")) ? "Restaurant" : "Store");
        ld.put("name", String.valueOf(sf.get("name")));
        ld.put("url", String.valueOf(site.get("baseUrl")));
        JsonNode settings = (JsonNode) site.get("settings");
        if (settings != null && settings.path("contact").hasNonNull("phone")) ld.put("telephone", settings.path("contact").get("phone").asText());
        if (settings != null && "RESTAURANT".equals(sf.get("storefrontType"))) {
            var specs = ld.putArray("openingHoursSpecification");
            settings.path("restaurant").path("openingHours").fields().forEachRemaining(e -> {
                if (e.getValue().path("closed").asBoolean(false)) return;
                String day = switch (e.getKey()) { case "MON" -> "Monday"; case "TUE" -> "Tuesday"; case "WED" -> "Wednesday"; case "THU" -> "Thursday"; case "FRI" -> "Friday"; case "SAT" -> "Saturday"; default -> "Sunday"; };
                specs.addObject().put("@type", "OpeningHoursSpecification").put("dayOfWeek", day).put("opens", e.getValue().path("open").asText()).put("closes", e.getValue().path("close").asText());
            });
        }
        return ld;
    }

    // ---- admin preview (authenticated by the caller) -------------------------------------------------------------

    /** Renders any version (including drafts) for the authenticated tenant. Never reachable without admin authentication. */
    public Map<String, Object> preview(CatalogPrincipal actor, long docId, Long versionId) {
        Map<String, Object> doc = storefronts.document(actor, docId);
        Map<String, Object> sf = storefronts.get(actor, ((Number) doc.get("storefrontId")).longValue());
        Map<String, Object> v = versionId != null ? storefronts.version(actor, docId, versionId)
                : storefronts.version(actor, docId, ((Number) storefronts.versions(actor, docId).get(0).get("id")).longValue());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("preview", true); out.put("versionNo", v.get("versionNo")); out.put("status", v.get("status")); out.put("docType", doc.get("docType"));
        out.put("content", renderer.render(actor.tenantId(), sf, (JsonNode) v.get("content")));
        return out;
    }
}
