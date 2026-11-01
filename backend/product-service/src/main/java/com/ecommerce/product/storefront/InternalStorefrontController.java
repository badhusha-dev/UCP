package com.ecommerce.product.storefront;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service-to-service lookup used by checkout: which tenant, store and storefront type a public storefront slug belongs to.
 * The slug is public and globally unique (V8 ux_storefront_slug), so a shopper can name it, but the tenant it maps to is
 * decided here, never by the caller. Not routed by the gateway; fails closed without a configured service token.
 */
@RestController
@RequestMapping("/internal/storefronts")
public class InternalStorefrontController {
    private final JdbcTemplate jdbc;

    @Value("${SERVICE_TO_SERVICE_TOKEN:}")
    private String serviceToken;

    public InternalStorefrontController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/{slug}")
    public Map<String, Object> resolve(@RequestHeader(value = "X-Service-Token", required = false) String token, @PathVariable String slug) {
        if (serviceToken == null || serviceToken.isBlank() || token == null
                || !MessageDigest.isEqual(serviceToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid service token is required.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT tenant_id AS \"tenantId\", store_id AS \"storeId\", storefront_type AS \"storefrontType\", slug FROM storefronts WHERE slug=? AND status='ACTIVE'",
                slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Storefront not found.");
        return rows.get(0);
    }
}
