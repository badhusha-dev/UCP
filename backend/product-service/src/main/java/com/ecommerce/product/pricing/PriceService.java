package com.ecommerce.product.pricing;

import com.ecommerce.product.security.CatalogPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

import static com.ecommerce.product.pricing.PricingSupport.*;

/** Product/variant price management, immutable price history, bulk updates, CSV exchange and scheduled prices. */
@Service
public class PriceService {
    private static final Logger log = LoggerFactory.getLogger(PriceService.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record PriceChange(Long productId, Long variantId, String currency, BigDecimal basePrice, BigDecimal salePrice,
                              BigDecimal costPrice, String storeId, String reason, Integer expectedVersion,
                              BigDecimal minSellingPrice, BigDecimal maxDiscountPercent, Boolean taxInclusive,
                              OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo) {
        public PriceChange(Long productId, Long variantId, String currency, BigDecimal basePrice, BigDecimal salePrice,
                           BigDecimal costPrice, String storeId, String reason, Integer expectedVersion) {
            this(productId, variantId, currency, basePrice, salePrice, costPrice, storeId, reason, expectedVersion, null, null, null, null, null);
        }
    }

    private final JdbcTemplate jdbc;
    private final PricingSupport support;
    private final PricingSettingsService settingsService;
    private final TransactionTemplate tx;

    public PriceService(JdbcTemplate jdbc, PricingSupport support, PricingSettingsService settingsService, PlatformTransactionManager tm) {
        this.jdbc = jdbc; this.support = support; this.settingsService = settingsService; this.tx = new TransactionTemplate(tm);
    }

    // ---- reads -------------------------------------------------------------------------------------------------

    public List<Map<String, Object>> list(CatalogPrincipal actor, Long productId, String store, int limit) {
        List<Object> args = new ArrayList<>(List.of(actor.tenantId()));
        String sql = "SELECT pp.*, p.name AS product_name, p.sku AS product_sku FROM product_prices pp JOIN product p ON p.id=pp.product_id WHERE pp.tenant_id=?";
        if (productId != null) { sql += " AND pp.product_id=?"; args.add(productId); }
        if (store != null && !store.isBlank()) { sql += " AND pp.store_id=?"; args.add(store); }
        sql += " ORDER BY pp.updated_at DESC, pp.id DESC LIMIT " + Math.max(1, Math.min(limit, 500));
        return support.rows(sql, args.toArray()).stream().map(r -> maskCost(actor, r)).toList();
    }

    public List<Map<String, Object>> history(CatalogPrincipal actor, Long productId, int limit) {
        List<Object> args = new ArrayList<>(List.of(actor.tenantId()));
        String sql = "SELECT * FROM price_history WHERE tenant_id=?";
        if (productId != null) { sql += " AND product_id=?"; args.add(productId); }
        sql += " ORDER BY changed_at DESC, id DESC LIMIT " + Math.max(1, Math.min(limit, 500));
        return support.rows(sql, args.toArray()).stream().map(r -> maskCost(actor, r)).toList();
    }

    private static Map<String, Object> maskCost(CatalogPrincipal actor, Map<String, Object> row) {
        if (actor.hasPermission("pricing:cost:read")) return row;
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.keySet().removeIf(k -> k.toLowerCase(Locale.ROOT).contains("costprice"));
        return out;
    }

    // ---- writes ------------------------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> setPrice(CatalogPrincipal actor, PriceChange c, String source, boolean mustBeNew) {
        if (c.productId() != null) {
            PricingSettingsService.Settings s = settingsService.effective(actor.tenantId(), storeId(c.storeId()));
            Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM product_prices WHERE tenant_id=? AND store_id=? AND product_id=? AND variant_id=? AND currency=?",
                    Integer.class, actor.tenantId(), storeId(c.storeId()), c.productId(), c.variantId() == null ? 0 : c.variantId(),
                    (c.currency() == null ? s.defaultCurrency() : c.currency()).toUpperCase(Locale.ROOT));
            if (mustBeNew && found != null && found > 0) throw conflict("A price already exists for this product, variant, store and currency; update it instead.");
            if (!mustBeNew && (found == null || found == 0)) throw notFound("Price");
        }
        return applyChange(actor, c, source);
    }

    private Map<String, Object> applyChange(CatalogPrincipal actor, PriceChange c, String source) {
        if (c.productId() == null) throw bad("productId is required.");
        String store = storeId(c.storeId());
        long variant = c.variantId() == null ? 0 : c.variantId();
        PricingSettingsService.Settings settings = settingsService.effective(actor.tenantId(), store);
        String currency = currency(c.currency() == null ? settings.defaultCurrency() : c.currency(), settings.supportedCurrencies());
        BigDecimal base = positive(c.basePrice(), "basePrice");
        BigDecimal sale = c.salePrice() == null ? null : positive(c.salePrice(), "salePrice");
        if (sale != null && sale.compareTo(base) > 0) throw bad("salePrice cannot exceed basePrice.");
        if (c.costPrice() != null) {
            if (!actor.hasPermission("pricing:cost:read")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cost price requires the pricing:cost:read permission.");
            if (c.costPrice().signum() < 0) throw bad("costPrice cannot be negative.");
        }
        Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id=? AND tenant_id=?", Integer.class, c.productId(), actor.tenantId());
        if (owned == null || owned == 0) throw notFound("Product");

        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT * FROM product_prices WHERE tenant_id=? AND store_id=? AND product_id=? AND variant_id=? AND currency=? FOR UPDATE",
                actor.tenantId(), store, c.productId(), variant, currency);
        BigDecimal cost = c.costPrice() != null ? c.costPrice().setScale(2, RoundingMode.HALF_UP)
                : existing.isEmpty() ? productCost(c.productId()) : (BigDecimal) existing.get(0).get("cost_price");
        BigDecimal effective = sale != null ? sale : base;
        Extras extras = extras(c, existing.isEmpty() ? null : existing.get(0), base, effective(sale, base));
        if (cost != null && cost.signum() > 0 && settings.minMarginPercent().signum() >= 0) {
            BigDecimal margin = effective.subtract(cost).multiply(HUNDRED).divide(effective, 2, RoundingMode.HALF_UP);
            if (margin.compareTo(settings.minMarginPercent()) < 0) {
                throw bad("Price violates the minimum margin of " + settings.minMarginPercent() + "% (would be " + margin + "%).");
            }
        }
        if (existing.isEmpty()) {
            long id = insertAndKey(actor, store, c.productId(), variant, currency, base, sale, cost);
            writeExtras(actor, id, extras);
            history(actor, id, store, c.productId(), variant, currency, null, base, null, sale, null, cost, source, c.reason());
            support.audit(actor, "PRICE", id, "CREATE", currency + " " + base + (sale != null ? " sale " + sale : ""));
            return one("SELECT * FROM product_prices WHERE id=? AND tenant_id=?", "Price", id, actor.tenantId());
        }
        Map<String, Object> row = existing.get(0);
        long id = ((Number) row.get("id")).longValue();
        int version = ((Number) row.get("version")).intValue();
        if (c.expectedVersion() != null && c.expectedVersion() != version) throw conflict("The price was changed by someone else; reload and retry.");
        BigDecimal oldBase = (BigDecimal) row.get("base_price");
        BigDecimal change = base.subtract(oldBase).abs().multiply(HUNDRED).divide(oldBase, 2, RoundingMode.HALF_UP);
        if ("MANUAL".equals(source) && change.compareTo(settings.approvalThresholdPercent()) > 0 && !actor.hasPermission("pricing:publish")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A base price change of " + change
                    + "% exceeds the approval threshold; schedule it for approval or ask a publisher.");
        }
        jdbc.update("UPDATE product_prices SET base_price=?, sale_price=?, cost_price=?, version=version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP "
                + "WHERE id=? AND tenant_id=?", base, sale, cost, actor.userId(), id, actor.tenantId());
        writeExtras(actor, id, extras);
        history(actor, id, store, c.productId(), variant, currency, oldBase, base, (BigDecimal) row.get("sale_price"), sale,
                (BigDecimal) row.get("cost_price"), cost, source, c.reason());
        support.audit(actor, "PRICE", id, "UPDATE", oldBase + " -> " + base + (sale != null ? " sale " + sale : ""));
        return one("SELECT * FROM product_prices WHERE id=? AND tenant_id=?", "Price", id, actor.tenantId());
    }

    private static BigDecimal effective(BigDecimal sale, BigDecimal base) { return sale != null ? sale : base; }

    private record Extras(BigDecimal minSelling, BigDecimal maxDiscount, boolean taxInclusive, OffsetDateTime from, OffsetDateTime to) {}

    /** Per-price guard rails and validity window; an omitted field keeps the stored value. */
    private Extras extras(PriceChange c, Map<String, Object> row, BigDecimal base, BigDecimal effective) {
        BigDecimal minSelling = c.minSellingPrice() != null ? positive(c.minSellingPrice(), "minSellingPrice") : row == null ? null : (BigDecimal) row.get("min_selling_price");
        BigDecimal maxDiscount = c.maxDiscountPercent() != null ? c.maxDiscountPercent() : row == null ? null : (BigDecimal) row.get("max_discount_percent");
        if (maxDiscount != null && (maxDiscount.signum() <= 0 || maxDiscount.compareTo(HUNDRED) > 0)) throw bad("maxDiscountPercent must be in (0, 100].");
        if (minSelling != null && minSelling.compareTo(effective) > 0) throw bad("The price (" + effective + ") is below the minimum selling price " + minSelling + ".");
        boolean tax = c.taxInclusive() != null ? c.taxInclusive() : row != null && Boolean.TRUE.equals(row.get("tax_inclusive"));
        OffsetDateTime from = c.effectiveFrom() != null ? c.effectiveFrom() : row == null ? null : asOffset(row.get("effective_from"));
        OffsetDateTime to = c.effectiveTo() != null ? c.effectiveTo() : row == null ? null : asOffset(row.get("effective_to"));
        if (from != null && to != null && !to.isAfter(from)) throw bad("effectiveTo must be after effectiveFrom.");
        return new Extras(minSelling, maxDiscount, tax, from, to);
    }

    private void writeExtras(CatalogPrincipal actor, long id, Extras e) {
        jdbc.update("UPDATE product_prices SET min_selling_price=?, max_discount_percent=?, tax_inclusive=?, effective_from=COALESCE(?, effective_from), effective_to=? WHERE id=? AND tenant_id=?",
                e.minSelling(), e.maxDiscount(), e.taxInclusive(), e.from(), e.to(), id, actor.tenantId());
    }

    private static OffsetDateTime asOffset(Object v) {
        if (v == null) return null;
        if (v instanceof OffsetDateTime o) return o;
        if (v instanceof java.sql.Timestamp t) return t.toInstant().atOffset(java.time.ZoneOffset.UTC);
        throw new IllegalStateException("Unexpected timestamp type " + v.getClass());
    }

    private BigDecimal productCost(Long productId) {
        List<BigDecimal> v = jdbc.queryForList("SELECT cost_price FROM product WHERE id=?", BigDecimal.class, productId);
        return v.isEmpty() || v.get(0) == null ? null : v.get(0).setScale(2, RoundingMode.HALF_UP);
    }

    private long insertAndKey(CatalogPrincipal actor, String store, long product, long variant, String currency, BigDecimal base,
                              BigDecimal sale, BigDecimal cost) {
        jdbc.update("INSERT INTO product_prices(tenant_id, store_id, product_id, variant_id, currency, base_price, sale_price, cost_price, created_by, updated_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                actor.tenantId(), store, product, variant, currency, base, sale, cost, actor.userId(), actor.userId());
        return jdbc.queryForObject("SELECT id FROM product_prices WHERE tenant_id=? AND store_id=? AND product_id=? AND variant_id=? AND currency=?",
                Long.class, actor.tenantId(), store, product, variant, currency);
    }

    private void history(CatalogPrincipal actor, long priceId, String store, long product, long variant, String currency,
                         BigDecimal oldBase, BigDecimal newBase, BigDecimal oldSale, BigDecimal newSale,
                         BigDecimal oldCost, BigDecimal newCost, String source, String reason) {
        jdbc.update("INSERT INTO price_history(tenant_id, store_id, product_price_id, product_id, variant_id, currency, old_base_price, new_base_price, "
                        + "old_sale_price, new_sale_price, old_cost_price, new_cost_price, change_source, reason, changed_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                actor.tenantId(), store, priceId, product, variant, currency, oldBase, newBase, oldSale, newSale, oldCost, newCost, source,
                reason == null ? null : reason.substring(0, Math.min(reason.length(), 255)), actor.userId());
    }

    /** All-or-nothing bulk update by percentage or absolute amount delta. {@code dryRun} previews without writing. */
    @Transactional
    public List<Map<String, Object>> bulkAdjust(CatalogPrincipal actor, List<Long> productIds, String mode, BigDecimal value,
                                                String currency, String store, String reason, boolean dryRun) {
        if (productIds == null || productIds.isEmpty()) throw bad("productIds is required.");
        if (productIds.size() > 500) throw bad("At most 500 products per bulk update.");
        if (!Set.of("PERCENT", "AMOUNT").contains(mode)) throw bad("mode must be PERCENT or AMOUNT.");
        if (value == null || value.signum() == 0) throw bad("value must be non-zero.");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long productId : new LinkedHashSet<>(productIds)) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM product_prices WHERE tenant_id=? AND product_id=? AND currency=? AND store_id=?",
                    actor.tenantId(), productId, currency == null ? "USD" : currency.toUpperCase(Locale.ROOT), storeId(store));
            if (rows.isEmpty()) throw bad("Product " + productId + " has no price in that currency/store to adjust.");
            Map<String, Object> r = rows.get(0);
            BigDecimal base = (BigDecimal) r.get("base_price");
            BigDecimal next = mode.equals("PERCENT") ? base.add(base.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP)) : base.add(value);
            BigDecimal sale = (BigDecimal) r.get("sale_price");
            if (sale != null && sale.compareTo(next) > 0) sale = next;
            if (next.signum() <= 0) throw bad("Product " + productId + " would get a non-positive price.");
            if (dryRun) {
                out.add(Map.of("productId", productId, "oldBasePrice", base, "newBasePrice", next.setScale(2, RoundingMode.HALF_UP)));
            } else {
                PriceChange change = new PriceChange(productId, ((Number) r.get("variant_id")).longValue(), (String) r.get("currency"),
                        next.setScale(2, RoundingMode.HALF_UP), sale, null, storeId(store), reason, null);
                BigDecimal cost = (BigDecimal) r.get("cost_price");
                BigDecimal effective = sale != null ? sale : next;
                PricingSettingsService.Settings s = settingsService.effective(actor.tenantId(), storeId(store));
                if (cost != null && cost.signum() > 0 && effective.subtract(cost).multiply(HUNDRED).divide(effective, 2, RoundingMode.HALF_UP)
                        .compareTo(s.minMarginPercent()) < 0) throw bad("Product " + productId + " would violate the minimum margin.");
                BigDecimal pct = next.subtract(base).abs().multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);
                if (pct.compareTo(s.approvalThresholdPercent()) > 0 && !actor.hasPermission("pricing:publish")) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Product " + productId + " change of " + pct + "% needs the pricing:publish permission.");
                }
                out.add(applyChange(actor, change, "BULK"));
            }
        }
        if (!dryRun) support.audit(actor, "PRICE", "bulk", "BULK_UPDATE", mode + " " + value + " on " + productIds.size() + " products");
        return out;
    }

    @Transactional
    public Map<String, Object> rollback(CatalogPrincipal actor, long historyId) {
        Map<String, Object> h = jdbc.queryForList("SELECT * FROM price_history WHERE id=? AND tenant_id=?", historyId, actor.tenantId())
                .stream().findFirst().orElseThrow(() -> notFound("Price history entry"));
        if (h.get("old_base_price") == null) throw bad("The first price of a product cannot be rolled back.");
        PriceChange back = new PriceChange(((Number) h.get("product_id")).longValue(), ((Number) h.get("variant_id")).longValue(),
                (String) h.get("currency"), (BigDecimal) h.get("old_base_price"), (BigDecimal) h.get("old_sale_price"), null,
                (String) h.get("store_id"), "Rollback of history #" + historyId, null);
        Map<String, Object> result = applyChange(actor, back, "ROLLBACK");
        support.audit(actor, "PRICE", historyId, "ROLLBACK", "history #" + historyId);
        return result;
    }

    // ---- CSV ---------------------------------------------------------------------------------------------------

    public String exportCsv(CatalogPrincipal actor) {
        boolean cost = actor.hasPermission("pricing:cost:read");
        StringBuilder sb = new StringBuilder("productId,variantId,storeId,currency,basePrice,salePrice" + (cost ? ",costPrice" : "") + "\n");
        for (Map<String, Object> r : list(actor, null, null, 500)) {
            sb.append(cell(r.get("productId"))).append(',').append(cell(r.get("variantId"))).append(',').append(cell(r.get("storeId"))).append(',')
                    .append(cell(r.get("currency"))).append(',').append(cell(r.get("basePrice"))).append(',').append(cell(r.get("salePrice")));
            if (cost) sb.append(',').append(cell(r.get("costPrice")));
            sb.append('\n');
        }
        support.audit(actor, "PRICE", "csv", "EXPORT", "rows exported");
        return sb.toString();
    }

    /** Spreadsheet formula injection guard: cells starting with = + - @ are prefixed with an apostrophe. */
    static String cell(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !s.matches("-?\\d+(\\.\\d+)?")) s = "'" + s;
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    /** Import is all-or-nothing: any invalid row aborts the whole file with a row-numbered error. */
    @Transactional
    public Map<String, Object> importCsv(CatalogPrincipal actor, String csv) {
        String[] lines = csv.split("\\r?\\n");
        if (lines.length < 2) throw bad("CSV needs a header and at least one row.");
        if (lines.length > 2001) throw bad("At most 2000 rows per import.");
        List<String> header = Arrays.stream(lines[0].split(",")).map(String::trim).toList();
        for (String required : List.of("productId", "currency", "basePrice")) if (!header.contains(required)) throw bad("Missing column " + required + ".");
        int count = 0;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            String[] cols = lines[i].split(",", -1);
            try {
                Map<String, String> row = new HashMap<>();
                for (int j = 0; j < header.size() && j < cols.length; j++) row.put(header.get(j), cols[j].trim());
                BigDecimal cost = row.getOrDefault("costPrice", "").isEmpty() ? null : new BigDecimal(row.get("costPrice"));
                applyChange(actor, new PriceChange(Long.parseLong(row.get("productId")),
                        row.getOrDefault("variantId", "").isEmpty() ? 0L : Long.parseLong(row.get("variantId")), row.get("currency"),
                        new BigDecimal(row.get("basePrice")), row.getOrDefault("salePrice", "").isEmpty() ? null : new BigDecimal(row.get("salePrice")),
                        cost, row.get("storeId"), "CSV import", null), "IMPORT");
                count++;
            } catch (NumberFormatException e) {
                throw bad("Row " + i + ": invalid number.");
            } catch (ResponseStatusException e) {
                throw bad("Row " + i + ": " + e.getReason());
            }
        }
        support.audit(actor, "PRICE", "csv", "IMPORT", count + " rows");
        return Map.of("imported", count);
    }

    // ---- scheduled prices --------------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> schedule(CatalogPrincipal actor, PriceChange c, OffsetDateTime from) {
        if (c.productId() == null) throw bad("productId is required.");
        required(from, "effectiveFrom");
        from = from.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        if (!from.isAfter(OffsetDateTime.now())) throw bad("effectiveFrom must be in the future.");
        String store = storeId(c.storeId());
        long variant = c.variantId() == null ? 0 : c.variantId();
        PricingSettingsService.Settings s = settingsService.effective(actor.tenantId(), store);
        String currency = currency(c.currency() == null ? s.defaultCurrency() : c.currency(), s.supportedCurrencies());
        BigDecimal base = positive(c.basePrice(), "basePrice");
        BigDecimal sale = c.salePrice() == null ? null : positive(c.salePrice(), "salePrice");
        if (sale != null && sale.compareTo(base) > 0) throw bad("salePrice cannot exceed basePrice.");
        Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id=? AND tenant_id=?", Integer.class, c.productId(), actor.tenantId());
        if (owned == null || owned == 0) throw notFound("Product");
        Integer clash = jdbc.queryForObject("SELECT COUNT(*) FROM scheduled_prices WHERE tenant_id=? AND store_id=? AND product_id=? AND variant_id=? AND currency=? "
                + "AND status IN ('PENDING_APPROVAL','SCHEDULED') AND effective_from=?", Integer.class, actor.tenantId(), store, c.productId(), variant, currency, from);
        if (clash != null && clash > 0) throw conflict("A price change is already scheduled for this product at that time.");
        jdbc.update("INSERT INTO scheduled_prices(tenant_id, store_id, product_id, variant_id, currency, new_base_price, new_sale_price, effective_from, reason, created_by, updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                actor.tenantId(), store, c.productId(), variant, currency, base, sale, from, c.reason(), actor.userId(), actor.userId());
        Map<String, Object> row = one("SELECT * FROM scheduled_prices WHERE tenant_id=? AND product_id=? AND variant_id=? AND currency=? AND store_id=? AND effective_from=? ORDER BY id DESC LIMIT 1",
                "Scheduled price", actor.tenantId(), c.productId(), variant, currency, store, from);
        long id = ((Number) row.get("id")).longValue();
        jdbc.update("INSERT INTO promotion_approvals(tenant_id, subject_type, subject_id, reason, requested_by) VALUES (?,?,?,?,?)",
                actor.tenantId(), "SCHEDULED_PRICE", id, c.reason(), actor.userId());
        support.audit(actor, "SCHEDULED_PRICE", id, "CREATE", base + " from " + from);
        return row;
    }

    public List<Map<String, Object>> listScheduled(CatalogPrincipal actor) {
        return support.rows("SELECT sp.*, p.name AS product_name FROM scheduled_prices sp JOIN product p ON p.id=sp.product_id WHERE sp.tenant_id=? ORDER BY sp.effective_from DESC LIMIT 500", actor.tenantId());
    }

    @Transactional
    public Map<String, Object> decideScheduled(CatalogPrincipal actor, long id, boolean approve, String note) {
        Map<String, Object> sp = one("SELECT * FROM scheduled_prices WHERE id=? AND tenant_id=? FOR UPDATE", "Scheduled price", id, actor.tenantId());
        if (!"PENDING_APPROVAL".equals(sp.get("status"))) throw conflict("Only pending scheduled prices can be decided.");
        if (((Number) sp.get("createdBy")).longValue() == actor.userId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot approve your own request.");
        jdbc.update("UPDATE scheduled_prices SET status=?, version=version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                approve ? "SCHEDULED" : "REJECTED", actor.userId(), id, actor.tenantId());
        jdbc.update("UPDATE promotion_approvals SET status=?, decided_by=?, decided_at=CURRENT_TIMESTAMP, decision_note=? WHERE tenant_id=? AND subject_type='SCHEDULED_PRICE' AND subject_id=? AND status='PENDING'",
                approve ? "APPROVED" : "REJECTED", actor.userId(), note, actor.tenantId(), id);
        support.audit(actor, "SCHEDULED_PRICE", id, approve ? "APPROVE" : "REJECT", note);
        return one("SELECT * FROM scheduled_prices WHERE id=? AND tenant_id=?", "Scheduled price", id, actor.tenantId());
    }

    @Transactional
    public Map<String, Object> cancelScheduled(CatalogPrincipal actor, long id) {
        Map<String, Object> sp = one("SELECT * FROM scheduled_prices WHERE id=? AND tenant_id=? FOR UPDATE", "Scheduled price", id, actor.tenantId());
        if (!Set.of("PENDING_APPROVAL", "SCHEDULED").contains(sp.get("status"))) throw conflict("Only pending or scheduled prices can be cancelled.");
        jdbc.update("UPDATE scheduled_prices SET status='CANCELLED', version=version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", actor.userId(), id, actor.tenantId());
        jdbc.update("UPDATE promotion_approvals SET status='WITHDRAWN', decided_by=?, decided_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND subject_type='SCHEDULED_PRICE' AND subject_id=? AND status='PENDING'",
                actor.userId(), actor.tenantId(), id);
        support.audit(actor, "SCHEDULED_PRICE", id, "CANCEL", null);
        return one("SELECT * FROM scheduled_prices WHERE id=? AND tenant_id=?", "Scheduled price", id, actor.tenantId());
    }

    /** Applies approved scheduled prices that are due. Each row is its own transaction so one bad row cannot block the rest. */
    @Scheduled(fixedDelayString = "${pricing.scheduler.delay-ms:60000}")
    public void applyDue() {
        List<Map<String, Object>> due = jdbc.queryForList("SELECT * FROM scheduled_prices WHERE status='SCHEDULED' AND effective_from <= CURRENT_TIMESTAMP ORDER BY effective_from LIMIT 200");
        for (Map<String, Object> sp : due) {
            try {
                tx.executeWithoutResult(status -> applyScheduledRow(((Number) sp.get("id")).longValue()));
            } catch (RuntimeException e) {
                log.warn("Scheduled price {} could not be applied: {}", sp.get("id"), e.getMessage());
            }
        }
    }

    public int applyDueNow() {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM scheduled_prices WHERE status='APPLIED'", Integer.class);
        applyDue();
        return jdbc.queryForObject("SELECT COUNT(*) FROM scheduled_prices WHERE status='APPLIED'", Integer.class) - before;
    }

    private void applyScheduledRow(long id) {
        Map<String, Object> sp = jdbc.queryForList("SELECT * FROM scheduled_prices WHERE id=? AND status='SCHEDULED' FOR UPDATE", id).stream().findFirst().orElse(null);
        if (sp == null) return;
        UUID tenant = (UUID) sp.get("tenant_id");
        CatalogPrincipal system = new CatalogPrincipal(((Number) sp.get("created_by")).longValue(), "scheduler@system", "SYSTEM", tenant,
                "ECOMMERCE_ADMIN", Set.of("*"));
        applyChange(system, new PriceChange(((Number) sp.get("product_id")).longValue(), ((Number) sp.get("variant_id")).longValue(),
                (String) sp.get("currency"), (BigDecimal) sp.get("new_base_price"), (BigDecimal) sp.get("new_sale_price"), null,
                (String) sp.get("store_id"), "Scheduled price #" + id, null), "SCHEDULED");
        jdbc.update("UPDATE scheduled_prices SET status='APPLIED', applied_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=?", id);
        support.audit(system, "SCHEDULED_PRICE", id, "APPLY", null);
    }

    // ---- dashboard -----------------------------------------------------------------------------------------------

    public Map<String, Object> dashboard(CatalogPrincipal actor) {
        UUID t = actor.tenantId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pricedProducts", jdbc.queryForObject("SELECT COUNT(DISTINCT product_id) FROM product_prices WHERE tenant_id=?", Long.class, t));
        out.put("totalProducts", jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE tenant_id=?", Long.class, t));
        out.put("scheduledPrices", jdbc.queryForObject("SELECT COUNT(*) FROM scheduled_prices WHERE tenant_id=? AND status='SCHEDULED'", Long.class, t));
        out.put("pendingApprovals", jdbc.queryForObject("SELECT COUNT(*) FROM promotion_approvals WHERE tenant_id=? AND status='PENDING'", Long.class, t));
        out.put("activeCampaigns", jdbc.queryForObject("SELECT COUNT(*) FROM promotion_campaigns WHERE tenant_id=? AND status='PUBLISHED' AND starts_at<=CURRENT_TIMESTAMP AND ends_at>CURRENT_TIMESTAMP", Long.class, t));
        out.put("upcomingCampaigns", jdbc.queryForObject("SELECT COUNT(*) FROM promotion_campaigns WHERE tenant_id=? AND status IN ('APPROVED','PUBLISHED') AND starts_at>CURRENT_TIMESTAMP", Long.class, t));
        out.put("activeCoupons", jdbc.queryForObject("SELECT COUNT(*) FROM coupon_codes WHERE tenant_id=? AND status='ACTIVE'", Long.class, t));
        out.put("redemptions", jdbc.queryForObject("SELECT COUNT(*) FROM coupon_redemptions WHERE tenant_id=? AND status='REDEEMED'", Long.class, t));
        out.put("discountGiven", jdbc.queryForObject("SELECT COALESCE(SUM(discount_amount),0) FROM coupon_redemptions WHERE tenant_id=? AND status='REDEEMED'", BigDecimal.class, t));
        out.put("recentPriceChanges", jdbc.queryForObject("SELECT COUNT(*) FROM price_history WHERE tenant_id=? AND changed_at > ?", Long.class, t, OffsetDateTime.now().minusDays(7)));
        out.put("expiredCampaigns", jdbc.queryForObject("SELECT COUNT(*) FROM promotion_campaigns WHERE tenant_id=? AND status IN ('APPROVED','PUBLISHED','PAUSED') AND ends_at<=CURRENT_TIMESTAMP", Long.class, t));
        out.put("productsWithSalePrice", jdbc.queryForObject("SELECT COUNT(DISTINCT product_id) FROM product_prices WHERE tenant_id=? AND status='ACTIVE' AND sale_price IS NOT NULL", Long.class, t));
        // Money is reported per currency; summing across currencies would be meaningless.
        out.put("promotionOrdersByCurrency", support.rows("SELECT currency, COUNT(*) AS orders, COALESCE(SUM(discount_total),0) AS discount_total, COALESCE(SUM(total_amount),0) AS revenue "
                + "FROM promotion_order_applications WHERE tenant_id=? AND status='APPLIED' AND discount_total > 0 GROUP BY currency ORDER BY currency", t));
        out.put("conversionRate", null);
        out.put("conversionRateNote", "Not available: the platform does not record storefront sessions or cart views, so a conversion denominator does not exist.");
        return out;
    }

    private Map<String, Object> one(String sql, String what, Object... args) { return support.one(sql, what, args); }
}
