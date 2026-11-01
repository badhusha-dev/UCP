package com.ecommerce.product.storefront;

import com.ecommerce.product.pricing.PricingSupport;
import com.ecommerce.product.security.CatalogPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.ecommerce.product.pricing.PricingSupport.*;

/** Built-in presets (code-defined, immutable) plus tenant-saved presets. Applying a preset just returns theme content to load into the draft. */
@Service
public class ThemePresetService {
    private final JdbcTemplate jdbc;
    private final PricingSupport support;
    private final ObjectMapper mapper;
    private final ContentValidator validator;
    private final StorefrontService storefronts;

    public ThemePresetService(JdbcTemplate jdbc, PricingSupport support, ObjectMapper mapper, ContentValidator validator, StorefrontService storefronts) {
        this.jdbc = jdbc; this.support = support; this.mapper = mapper; this.validator = validator; this.storefronts = storefronts;
    }

    private JsonNode builtin(String json) { try { return mapper.readTree(json); } catch (Exception e) { throw new IllegalStateException(e); } }

    public List<Map<String, Object>> list(CatalogPrincipal actor) {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(Map.of("id", "builtin:classic-light", "name", "Classic Light", "builtin", true, "content", builtin(
                "{\"colors\":{\"primary\":\"#1D4ED8\",\"secondary\":\"#475569\",\"accent\":\"#F59E0B\",\"background\":\"#FFFFFF\",\"surface\":\"#F8FAFC\",\"text\":\"#0F172A\"},\"headingFont\":\"Inter\",\"bodyFont\":\"Inter\",\"headerLayout\":\"CLASSIC\",\"footerLayout\":\"COLUMNS\",\"buttonStyle\":\"ROUNDED\",\"productCardStyle\":\"ELEVATED\",\"navigationStyle\":\"UNDERLINE\",\"mobileMenu\":\"DRAWER\",\"colorMode\":\"LIGHT\"}")));
        out.add(Map.of("id", "builtin:midnight", "name", "Midnight", "builtin", true, "content", builtin(
                "{\"colors\":{\"primary\":\"#818CF8\",\"secondary\":\"#94A3B8\",\"accent\":\"#34D399\",\"background\":\"#0B1020\",\"surface\":\"#141B34\",\"text\":\"#E2E8F0\"},\"headingFont\":\"Poppins\",\"bodyFont\":\"Inter\",\"headerLayout\":\"MINIMAL\",\"footerLayout\":\"SIMPLE\",\"buttonStyle\":\"PILL\",\"productCardStyle\":\"BORDERED\",\"navigationStyle\":\"PILL\",\"mobileMenu\":\"FULLSCREEN\",\"colorMode\":\"DARK\"}")));
        out.add(Map.of("id", "builtin:fresh-green", "name", "Fresh Green", "builtin", true, "content", builtin(
                "{\"colors\":{\"primary\":\"#15803D\",\"secondary\":\"#57534E\",\"accent\":\"#EA580C\",\"background\":\"#FEFCE8\",\"surface\":\"#FFFFFF\",\"text\":\"#1C1917\"},\"headingFont\":\"Playfair Display\",\"bodyFont\":\"Lato\",\"headerLayout\":\"CENTERED\",\"footerLayout\":\"COLUMNS\",\"buttonStyle\":\"SQUARE\",\"productCardStyle\":\"FLAT\",\"navigationStyle\":\"PLAIN\",\"mobileMenu\":\"BOTTOM_BAR\",\"colorMode\":\"AUTO\"}")));
        for (Map<String, Object> r : support.rows("SELECT id, name, content FROM storefront_theme_presets WHERE tenant_id=? ORDER BY name", actor.tenantId())) {
            out.add(Map.of("id", "tenant:" + r.get("id"), "name", r.get("name"), "builtin", false, "content", storefronts.parse((String) r.get("content"))));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> save(CatalogPrincipal actor, long storefrontId, String name, JsonNode theme) {
        storefronts.get(actor, storefrontId);
        String n = ContentValidator.plain(name, "name", 80, true);
        JsonNode valid = validator.validate("THEME", theme, storefronts.refs(actor.tenantId(), storefrontId));
        try {
            jdbc.update("INSERT INTO storefront_theme_presets(tenant_id, name, content, created_by) VALUES (?,?,?,?)", actor.tenantId(), n, valid.toString(), actor.userId());
        } catch (DuplicateKeyException e) { throw conflict("A preset named '" + n + "' already exists."); }
        storefronts.audit(actor, storefrontId, "THEME_PRESET", n, "CREATE", null);
        return Map.of("name", n, "content", valid);
    }

    @Transactional
    public void delete(CatalogPrincipal actor, long id) {
        int n = jdbc.update("DELETE FROM storefront_theme_presets WHERE id=? AND tenant_id=?", id, actor.tenantId());
        if (n == 0) throw notFound("Preset");
        storefronts.audit(actor, null, "THEME_PRESET", id, "DELETE", null);
    }
}
