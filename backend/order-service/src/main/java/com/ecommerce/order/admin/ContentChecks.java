package com.ecommerce.order.admin;

import java.util.regex.Pattern;

import static com.ecommerce.order.admin.AdminSupport.bad;

/** Plain-text validation for free-text fields entered by operators (no markup, bounded length). */
final class ContentChecks {
    private ContentChecks() {}
    private static final Pattern DANGEROUS = Pattern.compile("(?i)(<|>|javascript:|vbscript:|data:text|on[a-z]+\\s*=)");

    static String plain(String value, String field, int max) {
        if (value == null || value.isBlank()) throw bad(field + " is required.");
        String v = value.strip();
        if (v.length() > max) throw bad(field + " must be at most " + max + " characters.");
        if (DANGEROUS.matcher(v).find()) throw bad(field + " contains markup or script-like content, which is not allowed.");
        return v;
    }
}
