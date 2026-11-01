package com.ecommerce.product.storefront;

import com.ecommerce.product.pricing.PricingSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Turns stored content into what a storefront client renders: media ids become URLs, and product/category references are resolved
 * at read time from the authoritative catalog tables (nothing is copied into storefront content, so prices and stock are never stale).
 */
@Service
public class StorefrontRenderer {
    public static final String MEDIA_PATH = "/api/products/public/storefront/media/";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PricingSettingsService pricingSettings;

    public StorefrontRenderer(JdbcTemplate jdbc, ObjectMapper mapper, PricingSettingsService pricingSettings) { this.jdbc = jdbc; this.mapper = mapper; this.pricingSettings = pricingSettings; }

    public JsonNode render(UUID tenant, Map<String, Object> storefront, JsonNode content) {
        JsonNode copy = content.deepCopy();
        resolveMedia(tenant, ((Number) storefront.get("id")).longValue(), copy);
        JsonNode sections = copy.get("sections");
        if (sections != null && sections.isArray()) for (JsonNode s : sections) resolveSection(tenant, (String) storefront.get("storeId"), (ObjectNode) s);
        return copy;
    }

    private void resolveMedia(UUID tenant, long storefrontId, JsonNode node) {
        if (node.isObject()) {
            ObjectNode o = (ObjectNode) node;
            List<String> keys = new ArrayList<>();
            o.fieldNames().forEachRemaining(keys::add);
            for (String k : keys) {
                JsonNode v = o.get(k);
                if (k.endsWith("MediaId") && v.canConvertToLong()) {
                    List<Map<String, Object>> m = jdbc.queryForList("SELECT public_id, alt_text, width, height FROM storefront_media WHERE id=? AND tenant_id=? AND storefront_id=?", v.asLong(), tenant, storefrontId);
                    o.remove(k);
                    if (!m.isEmpty()) {
                        String base = k.substring(0, k.length() - "MediaId".length());
                        ObjectNode img = o.putObject(base.isEmpty() ? "image" : base + "Image");
                        img.put("url", MEDIA_PATH + m.get(0).get("public_id")).put("alt", String.valueOf(m.get(0).get("alt_text")))
                                .put("width", ((Number) m.get(0).get("width")).intValue()).put("height", ((Number) m.get(0).get("height")).intValue());
                    }
                } else resolveMedia(tenant, storefrontId, v);
            }
        } else if (node.isArray()) node.forEach(c -> resolveMedia(tenant, storefrontId, c));
    }

    private void resolveSection(UUID tenant, String storeId, ObjectNode section) {
        JsonNode props = section.get("props");
        if (props == null || !props.isObject()) return;
        ObjectNode p = (ObjectNode) props;
        switch (section.get("type").asText()) {
            case "FEATURED_PRODUCTS", "BEST_SELLERS", "TRENDING" -> {
                List<Long> ids = new ArrayList<>();
                p.path("productIds").forEach(i -> ids.add(i.asLong()));
                p.set("products", products(tenant, storeId, ids, null, null, 24, "IDS"));
            }
            case "NEW_ARRIVALS" -> p.set("products", products(tenant, storeId, List.of(), null, null, p.path("limit").asInt(8), "NEWEST"));
            case "PRODUCT_GRID" -> p.set("products", products(tenant, storeId, List.of(), p.hasNonNull("categoryId") ? p.get("categoryId").asLong() : null, null, p.path("limit").asInt(12), p.path("sort").asText("NEWEST")));
            case "FEATURED_CATEGORIES" -> {
                ArrayNode cats = mapper.createArrayNode();
                for (JsonNode id : p.path("categoryIds")) jdbc.queryForList("SELECT id, name, slug FROM product_category WHERE id=?", id.asLong())
                        .forEach(c -> cats.addObject().put("id", ((Number) c.get("id")).longValue()).put("name", String.valueOf(c.get("name"))).put("slug", String.valueOf(c.get("slug"))));
                p.set("categories", cats);
            }
            default -> { }
        }
    }

    private ArrayNode products(UUID tenant, String storeId, List<Long> ids, Long categoryId, String q, int limit, String sort) {
        StringBuilder sql = new StringBuilder("SELECT p.id, p.name, p.price, p.image_url, p.stock_quantity, p.category_id FROM product p WHERE p.tenant_id=? AND p.active=TRUE");
        List<Object> args = new ArrayList<>(List.of(tenant));
        if (sort.equals("IDS")) {
            if (ids.isEmpty()) return mapper.createArrayNode();
            sql.append(" AND p.id IN (").append(String.join(",", Collections.nCopies(ids.size(), "?"))).append(")");
            args.addAll(ids);
        }
        if (categoryId != null) { sql.append(" AND p.category_id=?"); args.add(categoryId); }
        sql.append(switch (sort) { case "PRICE_ASC" -> " ORDER BY p.price ASC, p.id"; case "PRICE_DESC" -> " ORDER BY p.price DESC, p.id"; case "NAME" -> " ORDER BY p.name, p.id"; default -> " ORDER BY p.id DESC"; });
        sql.append(" LIMIT ").append(Math.max(1, Math.min(limit, 48)));
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        if (sort.equals("IDS")) rows.sort(Comparator.comparingInt(r -> ids.indexOf(((Number) r.get("id")).longValue())));
        String currency = pricingSettings.effective(tenant, storeId).defaultCurrency();
        OffsetDateTime now = OffsetDateTime.now();
        ArrayNode out = mapper.createArrayNode();
        for (Map<String, Object> r : rows) {
            long id = ((Number) r.get("id")).longValue();
            ObjectNode n = out.addObject();
            n.put("id", id).put("name", String.valueOf(r.get("name")));
            BigDecimal price = BigDecimal.valueOf(((Number) r.get("price")).doubleValue()).setScale(2, RoundingMode.HALF_UP), sale = null;
            List<Map<String, Object>> pp = jdbc.queryForList("SELECT base_price, sale_price FROM product_prices WHERE tenant_id=? AND product_id=? AND variant_id=0 AND currency=? AND status='ACTIVE' AND (store_id=? OR store_id='*') AND effective_from<=? AND (effective_to IS NULL OR effective_to>?) ORDER BY CASE WHEN store_id='*' THEN 1 ELSE 0 END",
                    tenant, id, currency, storeId, now, now);
            if (!pp.isEmpty()) { price = (BigDecimal) pp.get(0).get("base_price"); sale = (BigDecimal) pp.get(0).get("sale_price"); }
            n.put("currency", currency).put("price", price);
            if (sale != null) n.put("salePrice", sale);
            if (r.get("image_url") != null) n.put("imageUrl", String.valueOf(r.get("image_url")));
            int stock = r.get("stock_quantity") == null ? 0 : ((Number) r.get("stock_quantity")).intValue();
            n.put("availability", stock <= 0 ? "OUT_OF_STOCK" : stock <= 5 ? "LOW_STOCK" : "IN_STOCK");
            if (r.get("category_id") != null) n.put("categoryId", ((Number) r.get("category_id")).longValue());
        }
        return out;
    }
}
