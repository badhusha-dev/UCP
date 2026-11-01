package com.ecommerce.product.storefront;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class ContentValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ContentValidator validator = new ContentValidator(mapper);
    private final ContentValidator.Refs refs = new ContentValidator.Refs(id -> id == 7, id -> id == 1 || id == 2);

    private JsonNode json(String s) throws Exception { return mapper.readTree(s); }

    @Test @DisplayName("safe URLs: only in-site paths and https")
    void urls() {
        assertEquals("/sale", ContentValidator.safeUrl("/sale", "u"));
        assertEquals("https://a.example/x", ContentValidator.safeUrl("https://a.example/x", "u"));
        assertNull(ContentValidator.safeUrl("  ", "u"));
        for (String bad : new String[]{"javascript:alert(1)", "JAVASCRIPT:alert(1)", "//evil.example", "http://a.example", "data:text/html,x", "/a/../b", "https://u:p@a.example", "ftp://a.example", "vbscript:x", "https://", "/x\\y"})
            assertThrows(ResponseStatusException.class, () -> ContentValidator.safeUrl(bad, "u"), bad);
    }

    @Test @DisplayName("plain text rejects markup and script-like content but allows ordinary punctuation")
    void plainText() {
        assertEquals("Fish & chips - 50% off!", ContentValidator.plain("Fish & chips - 50% off!", "t", 100, true));
        for (String bad : new String[]{"<b>x</b>", "a > b", "onclick=alert(1)", "&#60;script&#62;", "javascript:void(0)"})
            assertThrows(ResponseStatusException.class, () -> ContentValidator.plain(bad, "t", 100, true), bad);
        assertThrows(ResponseStatusException.class, () -> ContentValidator.plain("x".repeat(101), "t", 100, true));
        assertThrows(ResponseStatusException.class, () -> ContentValidator.plain(" ", "t", 100, true));
    }

    @Test @DisplayName("slugs: lowercase kebab-case, reserved words refused")
    void slugs() {
        assertEquals("summer-sale", ContentValidator.slug("summer-sale"));
        for (String bad : new String[]{"Summer", "a b", "-a", "a-", "a--b", "checkout", "api", "", "x".repeat(81)})
            assertThrows(ResponseStatusException.class, () -> ContentValidator.slug(bad), bad);
    }

    @Test @DisplayName("homepage content is normalised: defaults filled, unknown fields dropped, ids and references checked")
    void homepage() throws Exception {
        JsonNode out = validator.validate("HOMEPAGE", json("{\"sections\":[{\"id\":\"a\",\"type\":\"FEATURED_PRODUCTS\",\"junk\":1,\"props\":{\"productIds\":[1,2,2],\"x\":\"y\"}}],\"extra\":true}"), refs);
        assertFalse(out.has("extra"));
        assertEquals(2, out.at("/sections/0/props/productIds").size()); // de-duplicated
        assertTrue(out.at("/sections/0/visible").asBoolean());
        assertEquals(1, out.at("/sections/0/layout/mobile/columns").asInt());
        assertFalse(out.at("/sections/0/props").has("x"));
        assertThrows(ResponseStatusException.class, () -> validator.validate("HOMEPAGE", json("{\"sections\":[{\"id\":\"a\",\"type\":\"FEATURED_PRODUCTS\",\"props\":{\"productIds\":[99]}}]}"), refs));
        assertThrows(ResponseStatusException.class, () -> validator.validate("HOMEPAGE", json("{\"sections\":[{\"id\":\"a\",\"type\":\"HERO_BANNER\",\"props\":{\"desktopMediaId\":8}}]}"), refs));
        assertEquals(7, validator.validate("HOMEPAGE", json("{\"sections\":[{\"id\":\"a\",\"type\":\"HERO_BANNER\",\"props\":{\"desktopMediaId\":7}}]}"), refs).at("/sections/0/props/desktopMediaId").asInt());
        assertThrows(ResponseStatusException.class, () -> validator.validate("HOMEPAGE", json("{\"sections\":[{\"id\":\"a\",\"type\":\"NEW_ARRIVALS\",\"props\":{\"limit\":500}}]}"), refs));
        assertThrows(ResponseStatusException.class, () -> validator.validate("HOMEPAGE", mapper.createArrayNode(), refs));
    }

    @Test @DisplayName("oversized content is refused")
    void size() {
        var sections = mapper.createArrayNode();
        for (int i = 0; i < 40; i++) {
            var blocks = mapper.createArrayNode();
            for (int b = 0; b < 30; b++) blocks.addObject().put("type", "PARAGRAPH").put("text", "a".repeat(1900));
            var s = sections.addObject().put("id", "c" + i).put("type", "CUSTOM_CONTENT");
            s.putObject("props").set("blocks", blocks);
        }
        var content = mapper.createObjectNode();
        content.set("sections", sections);
        var e = assertThrows(ResponseStatusException.class, () -> validator.validate("HOMEPAGE", content, refs));
        assertTrue(e.getReason().contains("too large"));
    }

    @Test @DisplayName("settings: opening hours must be coherent")
    void hours() throws Exception {
        validator.validate("SETTINGS", json("{\"restaurant\":{\"openingHours\":{\"MON\":{\"open\":\"09:00\",\"close\":\"22:30\"},\"SUN\":{\"closed\":true}}}}"), refs);
        for (String bad : new String[]{"{\"MON\":{\"open\":\"9:00\",\"close\":\"22:00\"}}", "{\"MON\":{\"open\":\"22:00\",\"close\":\"22:00\"}}", "{\"XYZ\":{\"open\":\"09:00\",\"close\":\"10:00\"}}"})
            assertThrows(ResponseStatusException.class, () -> validator.validate("SETTINGS", json("{\"restaurant\":{\"openingHours\":" + bad + "}}"), refs), bad);
    }
}
