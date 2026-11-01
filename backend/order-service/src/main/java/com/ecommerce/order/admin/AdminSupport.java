package com.ecommerce.order.admin;

import com.ecommerce.order.admin.security.AdminPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.*;

/** Row shaping, error helpers and permission checks shared by the Order Management services. */
@Component
public class AdminSupport {
    private final JdbcTemplate jdbc;

    public AdminSupport(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    public static ResponseStatusException forbidden(String m) { return new ResponseStatusException(HttpStatus.FORBIDDEN, m); }
    public static ResponseStatusException notFound(String what) { return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found."); }
    public static ResponseStatusException conflict(String m) { return new ResponseStatusException(HttpStatus.CONFLICT, m); }

    public static void require(AdminPrincipal a, String permission) {
        if (!a.hasPermission(permission)) throw forbidden("Missing permission " + permission + ".");
    }

    public static Map<String, Object> camel(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> {
            StringBuilder sb = new StringBuilder();
            boolean up = false;
            for (char c : k.toLowerCase(Locale.ROOT).toCharArray()) { if (c == '_') up = true; else { sb.append(up ? Character.toUpperCase(c) : c); up = false; } }
            out.put(sb.toString(), v instanceof Timestamp t ? t.toInstant().atOffset(ZoneOffset.UTC) : v);
        });
        return out;
    }

    public List<Map<String, Object>> rows(String sql, Object... args) {
        return new ArrayList<>(jdbc.queryForList(sql, args).stream().map(AdminSupport::camel).toList());
    }

    public Map<String, Object> one(String sql, String what, Object... args) {
        List<Map<String, Object>> found = rows(sql, args);
        if (found.isEmpty()) throw notFound(what);
        return found.get(0);
    }

    public static String orderNumber(long id) { return String.format("ORD-%06d", id); }

    /** "ORD-000123", "123" and "#123" all find order 123; anything else is not an order number. */
    public static Long parseOrderNumber(String q) {
        if (q == null) return null;
        String s = q.trim().toUpperCase(Locale.ROOT).replaceFirst("^(ORD-|#)", "");
        return s.matches("\\d{1,18}") ? Long.parseLong(s) : null;
    }

    /** Spreadsheet formula injection guard for CSV exports. */
    public static String csvCell(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !s.matches("-?\\d+(\\.\\d+)?")) s = "'" + s;
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
