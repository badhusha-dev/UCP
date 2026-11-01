package com.ecommerce.product.storefront;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.function.LongPredicate;
import java.util.regex.Pattern;

import static com.ecommerce.product.pricing.PricingSupport.bad;

/**
 * Validates and normalises storefront content. Content is a closed vocabulary of typed blocks: unknown fields are dropped,
 * every string is plain text (markup, script URLs and event-handler patterns are rejected, never "cleaned up" silently),
 * and every link is either an in-site path or an https URL. Nothing here is ever evaluated.
 */
@Component
public class ContentValidator {
    public static final Set<String> SECTION_TYPES = Set.of("HERO_BANNER", "PROMO_BANNER", "FEATURED_PRODUCTS", "FEATURED_CATEGORIES", "PRODUCT_GRID",
            "BEST_SELLERS", "NEW_ARRIVALS", "TRENDING", "TESTIMONIALS", "BRAND_HIGHLIGHTS", "COUNTDOWN", "NEWSLETTER", "CUSTOM_CONTENT");
    public static final Set<String> FONTS = Set.of("Inter", "Roboto", "Open Sans", "Lato", "Montserrat", "Poppins", "Merriweather", "Playfair Display", "Source Sans 3", "Nunito");
    static final Set<String> HEADER_LAYOUTS = Set.of("CLASSIC", "CENTERED", "MINIMAL", "MEGA");
    static final Set<String> FOOTER_LAYOUTS = Set.of("SIMPLE", "COLUMNS", "MINIMAL");
    static final Set<String> BUTTON_STYLES = Set.of("SQUARE", "ROUNDED", "PILL");
    static final Set<String> CARD_STYLES = Set.of("FLAT", "ELEVATED", "BORDERED");
    static final Set<String> NAV_STYLES = Set.of("UNDERLINE", "PILL", "PLAIN");
    static final Set<String> MOBILE_MENUS = Set.of("DRAWER", "FULLSCREEN", "BOTTOM_BAR");
    static final Set<String> COLOR_MODES = Set.of("LIGHT", "DARK", "AUTO");
    static final Set<String> ROBOTS = Set.of("INDEX_FOLLOW", "NOINDEX_FOLLOW", "INDEX_NOFOLLOW", "NOINDEX_NOFOLLOW");
    static final Set<String> SORTS = Set.of("NEWEST", "PRICE_ASC", "PRICE_DESC", "NAME");
    static final Set<String> BADGES = Set.of("NEW", "SALE", "LOW_STOCK", "SOLD_OUT", "FEATURED");
    static final Set<String> DAYS = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    static final Set<String> TESTIMONIAL_FIELDS = Set.of("quote", "author", "role");
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Pattern DANGEROUS = Pattern.compile("(?i)(<|>|javascript:|data:text|vbscript:|on[a-z]+\\s*=|&#x?[0-9a-f]+;|\\u0000)");
    private static final int MAX_SECTIONS = 50;
    private static final int MAX_BYTES = 200_000;

    private final ObjectMapper mapper;

    public ContentValidator(ObjectMapper mapper) { this.mapper = mapper; }

    /** Cross-service lookups the validator needs, supplied by the caller so this class stays pure. */
    public record Refs(LongPredicate mediaOwned, LongPredicate productOwned) {}

    public JsonNode validate(String docType, JsonNode content, Refs refs) {
        if (content == null || !content.isObject()) throw bad("Content must be a JSON object.");
        try { if (mapper.writeValueAsBytes(content).length > MAX_BYTES) throw bad("Content is too large (max 200 KB)."); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw bad("Content is not valid JSON."); }
        return switch (docType) {
            case "HOMEPAGE", "LANDING" -> page(content, refs, docType.equals("LANDING"));
            case "THEME" -> theme(content, refs);
            case "NAVIGATION" -> navigation(content);
            case "SETTINGS" -> settings(content);
            default -> throw bad("Unknown document type.");
        };
    }

    // ---- pages ---------------------------------------------------------------------------------------------------

    private JsonNode page(JsonNode in, Refs refs, boolean landing) {
        ObjectNode out = mapper.createObjectNode();
        if (landing) out.put("template", oneOf(in, "template", Set.of("BLANK", "PROMOTION", "COLLECTION", "ABOUT", "CONTACT"), "BLANK"));
        ArrayNode sections = out.putArray("sections");
        JsonNode list = in.get("sections");
        if (list != null && !list.isArray()) throw bad("sections must be an array.");
        if (list != null && list.size() > MAX_SECTIONS) throw bad("A page can have at most " + MAX_SECTIONS + " sections.");
        Set<String> ids = new HashSet<>();
        if (list != null) for (JsonNode s : list) sections.add(section(s, refs, ids));
        out.set("seo", seo(in.get("seo"), refs));
        return out;
    }

    private ObjectNode section(JsonNode s, Refs refs, Set<String> ids) {
        if (!s.isObject()) throw bad("Each section must be an object.");
        String type = text(s, "type", 40, true);
        if (!SECTION_TYPES.contains(type)) throw bad("Unsupported section type " + type + ".");
        String id = text(s, "id", 40, true);
        if (!id.matches("[A-Za-z0-9_-]{1,40}")) throw bad("Section id may contain letters, digits, '_' and '-'.");
        if (!ids.add(id)) throw bad("Duplicate section id " + id + ".");
        ObjectNode o = mapper.createObjectNode();
        o.put("id", id); o.put("type", type);
        o.put("visible", s.path("visible").asBoolean(true));
        ObjectNode layout = o.putObject("layout");
        for (String device : List.of("desktop", "mobile")) {
            JsonNode d = s.path("layout").path(device);
            ObjectNode dn = layout.putObject(device);
            dn.put("columns", intIn(d, "columns", 1, 6, device.equals("desktop") ? 3 : 1));
            dn.put("visible", d.path("visible").asBoolean(true));
        }
        JsonNode p = s.path("props");
        ObjectNode props = o.putObject("props");
        switch (type) {
            case "HERO_BANNER", "PROMO_BANNER" -> {
                put(props, "heading", text(p, "heading", 120, false)); put(props, "subheading", text(p, "subheading", 240, false));
                put(props, "buttonLabel", text(p, "buttonLabel", 40, false)); put(props, "buttonUrl", url(p, "buttonUrl"));
                media(props, p, "desktopMediaId", refs); media(props, p, "mobileMediaId", refs);
                put(props, "altText", text(p, "altText", 255, false));
            }
            case "FEATURED_PRODUCTS", "BEST_SELLERS", "TRENDING" -> {
                put(props, "heading", text(p, "heading", 120, false));
                props.set("productIds", ids(p, "productIds", 24, refs.productOwned(), "product"));
                if (props.get("productIds").isEmpty()) throw bad(type + " needs a hand-picked product list: automatic ranking needs a sales feed that this platform does not provide.");
            }
            case "NEW_ARRIVALS" -> { put(props, "heading", text(p, "heading", 120, false)); props.put("limit", intIn(p, "limit", 1, 24, 8)); }
            case "PRODUCT_GRID" -> {
                put(props, "heading", text(p, "heading", 120, false)); props.put("limit", intIn(p, "limit", 1, 48, 12));
                if (p.hasNonNull("categoryId")) props.put("categoryId", positive(p, "categoryId"));
                props.put("sort", oneOf(p, "sort", SORTS, "NEWEST"));
            }
            case "FEATURED_CATEGORIES" -> {
                put(props, "heading", text(p, "heading", 120, false));
                ArrayNode cats = props.putArray("categoryIds");
                if (p.has("categoryIds")) for (JsonNode c : p.get("categoryIds")) { if (!c.canConvertToLong() || c.asLong() <= 0) throw bad("categoryIds must be positive ids."); cats.add(c.asLong()); }
                if (cats.size() > 12) throw bad("At most 12 categories.");
            }
            case "TESTIMONIALS" -> {
                put(props, "heading", text(p, "heading", 120, false));
                ArrayNode items = props.putArray("items");
                if (p.has("items")) { if (!p.get("items").isArray() || p.get("items").size() > 12) throw bad("At most 12 testimonials."); for (JsonNode t : p.get("items")) {
                    ObjectNode n = items.addObject();
                    for (String f : TESTIMONIAL_FIELDS) put(n, f, text(t, f, f.equals("quote") ? 400 : 80, f.equals("quote")));
                } }
            }
            case "BRAND_HIGHLIGHTS" -> {
                put(props, "heading", text(p, "heading", 120, false));
                ArrayNode items = props.putArray("items");
                if (p.has("items")) { if (!p.get("items").isArray() || p.get("items").size() > 8) throw bad("At most 8 highlights."); for (JsonNode t : p.get("items")) {
                    ObjectNode n = items.addObject(); put(n, "title", text(t, "title", 80, true)); put(n, "description", text(t, "description", 200, false)); media(n, t, "mediaId", refs);
                } }
            }
            case "COUNTDOWN" -> {
                put(props, "heading", text(p, "heading", 120, false));
                String ends = text(p, "endsAt", 40, true);
                try { java.time.OffsetDateTime.parse(ends); } catch (Exception e) { throw bad("endsAt must be an ISO-8601 date-time with offset."); }
                props.put("endsAt", ends); put(props, "buttonLabel", text(p, "buttonLabel", 40, false)); put(props, "buttonUrl", url(p, "buttonUrl"));
            }
            case "NEWSLETTER" -> { put(props, "heading", text(p, "heading", 120, false)); put(props, "description", text(p, "description", 240, false)); put(props, "buttonLabel", text(p, "buttonLabel", 40, false)); }
            case "CUSTOM_CONTENT" -> {
                ArrayNode blocks = props.putArray("blocks");
                if (p.has("blocks")) { if (!p.get("blocks").isArray() || p.get("blocks").size() > 30) throw bad("At most 30 content blocks."); for (JsonNode b : p.get("blocks")) {
                    ObjectNode n = blocks.addObject();
                    String bt = oneOf(b, "type", Set.of("HEADING", "PARAGRAPH", "LIST", "IMAGE", "BUTTON"), null);
                    n.put("type", bt);
                    switch (bt) {
                        case "HEADING", "PARAGRAPH" -> put(n, "text", text(b, "text", bt.equals("HEADING") ? 160 : 2000, true));
                        case "LIST" -> { ArrayNode li = n.putArray("items"); if (!b.path("items").isArray() || b.get("items").size() > 20) throw bad("A list needs 1-20 items.");
                            for (JsonNode i : b.get("items")) li.add(plain(i.asText(""), "list item", 200, true)); }
                        case "IMAGE" -> { media(n, b, "mediaId", refs); put(n, "altText", text(b, "altText", 255, true)); }
                        default -> { put(n, "label", text(b, "label", 40, true)); put(n, "url", url(b, "url")); }
                    }
                } }
            }
            default -> throw bad("Unsupported section type.");
        }
        return o;
    }

    // ---- SEO -----------------------------------------------------------------------------------------------------

    ObjectNode seo(JsonNode s, Refs refs) {
        JsonNode in = s == null ? mapper.createObjectNode() : s;
        ObjectNode o = mapper.createObjectNode();
        put(o, "title", text(in, "title", 70, false)); put(o, "description", text(in, "description", 160, false));
        String canonical = url(in, "canonicalUrl");
        if (canonical != null && !canonical.startsWith("https://")) throw bad("canonicalUrl must be an absolute https URL.");
        put(o, "canonicalUrl", canonical);
        o.put("robots", oneOf(in, "robots", ROBOTS, "INDEX_FOLLOW"));
        put(o, "ogTitle", text(in, "ogTitle", 90, false)); put(o, "ogDescription", text(in, "ogDescription", 200, false));
        media(o, in, "ogImageMediaId", refs);
        return o;
    }

    // ---- theme ---------------------------------------------------------------------------------------------------

    private JsonNode theme(JsonNode in, Refs refs) {
        ObjectNode o = mapper.createObjectNode();
        media(o, in, "logoMediaId", refs); media(o, in, "faviconMediaId", refs);
        ObjectNode colors = o.putObject("colors");
        for (String key : List.of("primary", "secondary", "accent", "background", "surface", "text")) {
            String v = text(in.path("colors"), key, 7, false);
            if (v != null && !HEX.matcher(v).matches()) throw bad("Color " + key + " must be a #RRGGBB hex value.");
            put(colors, key, v);
        }
        String font = text(in, "headingFont", 40, false), body = text(in, "bodyFont", 40, false);
        for (String f : new String[]{font, body}) if (f != null && !FONTS.contains(f)) throw bad("Font " + f + " is not supported. Choose from " + new TreeSet<>(FONTS) + ".");
        put(o, "headingFont", font); put(o, "bodyFont", body);
        o.put("headerLayout", oneOf(in, "headerLayout", HEADER_LAYOUTS, "CLASSIC"));
        o.put("footerLayout", oneOf(in, "footerLayout", FOOTER_LAYOUTS, "COLUMNS"));
        o.put("buttonStyle", oneOf(in, "buttonStyle", BUTTON_STYLES, "ROUNDED"));
        o.put("productCardStyle", oneOf(in, "productCardStyle", CARD_STYLES, "ELEVATED"));
        o.put("navigationStyle", oneOf(in, "navigationStyle", NAV_STYLES, "UNDERLINE"));
        o.put("mobileMenu", oneOf(in, "mobileMenu", MOBILE_MENUS, "DRAWER"));
        o.put("colorMode", oneOf(in, "colorMode", COLOR_MODES, "LIGHT"));
        return o;
    }

    // ---- navigation ----------------------------------------------------------------------------------------------

    private JsonNode navigation(JsonNode in) {
        ObjectNode o = mapper.createObjectNode();
        int[] total = {0};
        for (String menu : List.of("header", "footer", "mobile")) {
            ArrayNode arr = o.putArray(menu);
            JsonNode items = in.get(menu);
            if (items != null) { if (!items.isArray()) throw bad(menu + " must be an array."); for (JsonNode i : items) arr.add(navItem(i, 1, total)); }
        }
        return o;
    }

    private ObjectNode navItem(JsonNode i, int depth, int[] total) {
        if (depth > 3) throw bad("Navigation can be at most 3 levels deep.");
        if (++total[0] > 100) throw bad("Navigation can have at most 100 items.");
        ObjectNode n = mapper.createObjectNode();
        put(n, "label", text(i, "label", 60, true));
        String url = url(i, "url");
        if (url == null && !i.hasNonNull("categoryId") && !i.has("children")) throw bad("Navigation item '" + n.get("label").asText() + "' needs a link, a category or children.");
        put(n, "url", url);
        if (i.hasNonNull("categoryId")) n.put("categoryId", positive(i, "categoryId"));
        n.put("promotional", i.path("promotional").asBoolean(false));
        ArrayNode kids = n.putArray("children");
        if (i.has("children")) { if (!i.get("children").isArray()) throw bad("children must be an array."); for (JsonNode c : i.get("children")) kids.add(navItem(c, depth + 1, total)); }
        return n;
    }

    // ---- settings ------------------------------------------------------------------------------------------------

    private JsonNode settings(JsonNode in) {
        ObjectNode o = mapper.createObjectNode();
        JsonNode d = in.path("display");
        ObjectNode display = o.putObject("display");
        display.put("showAvailability", d.path("showAvailability").asBoolean(true));
        display.put("showSearch", d.path("showSearch").asBoolean(true));
        display.put("showFilters", d.path("showFilters").asBoolean(true));
        display.put("defaultSort", oneOf(d, "defaultSort", SORTS, "NEWEST"));
        display.put("productsPerPage", intIn(d, "productsPerPage", 4, 96, 24));
        ArrayNode badges = display.putArray("badges");
        if (d.has("badges")) { if (!d.get("badges").isArray()) throw bad("badges must be an array."); for (JsonNode b : d.get("badges")) { if (!BADGES.contains(b.asText())) throw bad("Unsupported badge " + b.asText() + "."); badges.add(b.asText()); } }
        JsonNode r = in.path("restaurant");
        ObjectNode rest = o.putObject("restaurant");
        rest.put("orderingAvailable", r.path("orderingAvailable").asBoolean(false));
        rest.put("showVegIndicator", r.path("showVegIndicator").asBoolean(true));
        rest.put("showPreparationTime", r.path("showPreparationTime").asBoolean(false));
        ObjectNode hours = rest.putObject("openingHours");
        JsonNode h = r.path("openingHours");
        if (h.isObject()) h.fields().forEachRemaining(e -> {
            if (!DAYS.contains(e.getKey())) throw bad("Unknown weekday " + e.getKey() + ".");
            JsonNode v = e.getValue();
            if (v.path("closed").asBoolean(false)) { hours.putObject(e.getKey()).put("closed", true); return; }
            String open = v.path("open").asText(""), close = v.path("close").asText("");
            if (!TIME.matcher(open).matches() || !TIME.matcher(close).matches()) throw bad("Opening hours for " + e.getKey() + " must be HH:mm.");
            if (open.compareTo(close) >= 0) throw bad("Opening time must be before closing time on " + e.getKey() + " (split overnight hours across two days).");
            hours.putObject(e.getKey()).put("open", open).put("close", close);
        });
        JsonNode c = in.path("contact");
        ObjectNode contact = o.putObject("contact");
        put(contact, "name", text(c, "name", 120, false)); put(contact, "phone", text(c, "phone", 30, false)); put(contact, "email", text(c, "email", 120, false));
        if (contact.hasNonNull("email") && !contact.get("email").asText().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw bad("Contact email is not valid.");
        return o;
    }

    // ---- primitives ----------------------------------------------------------------------------------------------

    public static String plain(String value, String field, int max, boolean required) {
        if (value == null || value.isBlank()) { if (required) throw bad(field + " is required."); return null; }
        String v = value.strip();
        if (v.length() > max) throw bad(field + " must be at most " + max + " characters.");
        if (DANGEROUS.matcher(v).find()) throw bad(field + " contains markup or script-like content, which is not allowed.");
        return v;
    }

    private String text(JsonNode n, String field, int max, boolean required) {
        JsonNode v = n == null ? null : n.get(field);
        if (v == null || v.isNull()) { if (required) throw bad(field + " is required."); return null; }
        if (!v.isTextual()) throw bad(field + " must be text.");
        return plain(v.asText(), field, max, required);
    }

    private String oneOf(JsonNode n, String field, Set<String> allowed, String fallback) {
        JsonNode v = n == null ? null : n.get(field);
        if (v == null || v.isNull() || (v.isTextual() && v.asText().isBlank())) { if (fallback == null) throw bad(field + " is required."); return fallback; }
        if (!allowed.contains(v.asText())) throw bad(field + " must be one of " + new TreeSet<>(allowed) + ".");
        return v.asText();
    }

    private int intIn(JsonNode n, String field, int min, int max, int fallback) {
        JsonNode v = n == null ? null : n.get(field);
        if (v == null || v.isNull()) return fallback;
        if (!v.isInt() || v.asInt() < min || v.asInt() > max) throw bad(field + " must be a whole number between " + min + " and " + max + ".");
        return v.asInt();
    }

    private long positive(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || !v.canConvertToLong() || v.asLong() <= 0) throw bad(field + " must be a positive id.");
        return v.asLong();
    }

    private void media(ObjectNode out, JsonNode in, String field, Refs refs) {
        if (in == null || !in.hasNonNull(field)) return;
        long id = positive(in, field);
        if (!refs.mediaOwned().test(id)) throw bad("Media " + id + " does not exist in this storefront.");
        out.put(field, id);
    }

    private ArrayNode ids(JsonNode in, String field, int max, LongPredicate owned, String label) {
        ArrayNode a = mapper.createArrayNode();
        JsonNode v = in.get(field);
        if (v == null) return a;
        if (!v.isArray() || v.size() > max) throw bad(field + " must be a list of at most " + max + " ids.");
        Set<Long> seen = new LinkedHashSet<>();
        for (JsonNode e : v) {
            if (!e.canConvertToLong() || e.asLong() <= 0) throw bad(field + " must contain positive ids.");
            if (!owned.test(e.asLong())) throw bad("The " + label + " " + e.asLong() + " does not exist in this tenant.");
            seen.add(e.asLong());
        }
        seen.forEach(a::add);
        return a;
    }

    /** A link must be an in-site path ("/sale") or an absolute https URL; nothing else (no javascript:, data:, protocol-relative, credentials). */
    public static String safeUrl(String raw, String field) {
        String v = plain(raw, field, 500, false);
        if (v == null) return null;
        if (v.startsWith("/") && !v.startsWith("//") && !v.contains("\\") && !v.contains("..")) return v;
        if (v.startsWith("https://")) {
            try {
                URI u = URI.create(v);
                if (u.getHost() == null || u.getUserInfo() != null) throw bad(field + " is not a valid https URL.");
                return v;
            } catch (IllegalArgumentException e) { throw bad(field + " is not a valid URL."); }
        }
        throw bad(field + " must be an in-site path starting with '/' or an https:// URL.");
    }

    private String url(JsonNode n, String field) { return safeUrl(text(n, field, 500, false), field); }

    public static String slug(String value) {
        if (value == null || !SLUG.matcher(value).matches() || value.length() > 80) throw bad("Slug may contain lowercase letters, digits and single hyphens (max 80).");
        if (Set.of("admin", "api", "static", "assets", "cart", "checkout", "login", "account").contains(value)) throw bad("Slug '" + value + "' is reserved.");
        return value;
    }

    private static void put(ObjectNode n, String field, String value) { if (value != null) n.put(field, value); }
}
