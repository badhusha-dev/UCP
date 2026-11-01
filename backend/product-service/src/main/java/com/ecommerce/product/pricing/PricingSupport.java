package com.ecommerce.product.pricing;

import com.ecommerce.product.security.CatalogPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Shared helpers: row shaping, audit writes and input validation for the pricing module. */
@Component
public class PricingSupport {
    private final JdbcTemplate jdbc;

    public PricingSupport(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    public static ResponseStatusException notFound(String what) { return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found."); }
    public static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    public void audit(CatalogPrincipal actor, String entityType, Object entityId, String action, String detail) {
        jdbc.update("INSERT INTO promotion_audit_logs(tenant_id, entity_type, entity_id, action, actor_id, actor_email, detail) VALUES (?,?,?,?,?,?,?)",
                actor.tenantId(), entityType, String.valueOf(entityId), action, actor.userId(), actor.email(), detail);
    }

    /** snake_case column labels become camelCase JSON keys. */
    public static Map<String, Object> camel(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> {
            StringBuilder sb = new StringBuilder();
            boolean up = false;
            for (char c : k.toLowerCase(Locale.ROOT).toCharArray()) {
                if (c == '_') up = true; else { sb.append(up ? Character.toUpperCase(c) : c); up = false; }
            }
            out.put(sb.toString(), v instanceof java.sql.Timestamp t ? t.toInstant().atOffset(ZoneOffset.UTC) : v);
        });
        return out;
    }

    public List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql, args).stream().map(PricingSupport::camel).toList();
    }

    public Map<String, Object> one(String sql, String what, Object... args) {
        List<Map<String, Object>> found = rows(sql, args);
        if (found.isEmpty()) throw notFound(what);
        return found.get(0);
    }

    public static String currency(String code, Set<String> supported) {
        if (code == null || !code.matches("[A-Za-z]{3}")) throw bad("Currency must be a 3-letter ISO 4217 code.");
        String upper = code.toUpperCase(Locale.ROOT);
        try { Currency.getInstance(upper); } catch (IllegalArgumentException e) { throw bad("Unknown currency " + upper + "."); }
        if (!supported.contains(upper)) throw bad("Currency " + upper + " is not enabled for this tenant.");
        return upper;
    }

    public static BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) throw bad(field + " must be greater than zero.");
        if (value.scale() > 2 && value.stripTrailingZeros().scale() > 2) throw bad(field + " allows at most 2 decimal places.");
        if (value.compareTo(new BigDecimal("99999999999.99")) > 0) throw bad(field + " is too large.");
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public static OffsetDateTime required(OffsetDateTime value, String field) {
        if (value == null) throw bad(field + " is required.");
        return value;
    }

    public static String storeId(String store) { return store == null || store.isBlank() ? "*" : store.trim(); }

    public static String text(String value, String field, int max) {
        if (value == null || value.isBlank()) throw bad(field + " is required.");
        if (value.length() > max) throw bad(field + " must be at most " + max + " characters.");
        return value.trim();
    }
}
