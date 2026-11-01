package com.ecommerce.product.storefront;

import com.ecommerce.product.pricing.PricingSupport;
import com.ecommerce.product.security.CatalogPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

import static com.ecommerce.product.pricing.PricingSupport.*;

/**
 * Storefronts, versioned content documents and the publishing workflow
 * DRAFT -> IN_REVIEW -> APPROVED -> PUBLISHED -> ARCHIVED. Published versions are never edited: a new edit starts a new DRAFT
 * version, and the live site keeps serving the published snapshot until a new version is explicitly published.
 */
@Service
public class StorefrontService {
    private static final Logger log = LoggerFactory.getLogger(StorefrontService.class);

    private final JdbcTemplate jdbc;
    private final PricingSupport support;
    private final ContentValidator validator;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public StorefrontService(JdbcTemplate jdbc, PricingSupport support, ContentValidator validator, ObjectMapper mapper, PlatformTransactionManager tm) {
        this.jdbc = jdbc; this.support = support; this.validator = validator; this.mapper = mapper; this.tx = new TransactionTemplate(tm);
    }

    // ---- audit ---------------------------------------------------------------------------------------------------

    void audit(CatalogPrincipal actor, Long storefrontId, String type, Object id, String action, String detail) {
        jdbc.update("INSERT INTO storefront_audit_logs(tenant_id, storefront_id, entity_type, entity_id, action, actor_id, actor_email, detail) VALUES (?,?,?,?,?,?,?,?)",
                actor.tenantId(), storefrontId, type, String.valueOf(id), action, actor.userId(), actor.email(), detail == null ? null : detail.substring(0, Math.min(detail.length(), 2000)));
    }

    public List<Map<String, Object>> auditLog(CatalogPrincipal actor, Long storefrontId, int limit) {
        if (storefrontId == null) return support.rows("SELECT * FROM storefront_audit_logs WHERE tenant_id=? ORDER BY created_at DESC, id DESC LIMIT " + clamp(limit), actor.tenantId());
        return support.rows("SELECT * FROM storefront_audit_logs WHERE tenant_id=? AND storefront_id=? ORDER BY created_at DESC, id DESC LIMIT " + clamp(limit), actor.tenantId(), storefrontId);
    }

    private static int clamp(int limit) { return Math.max(1, Math.min(limit, 500)); }

    // ---- storefronts ---------------------------------------------------------------------------------------------

    public List<Map<String, Object>> list(CatalogPrincipal actor) {
        return support.rows("SELECT * FROM storefronts WHERE tenant_id=? ORDER BY id", actor.tenantId());
    }

    public Map<String, Object> get(CatalogPrincipal actor, long id) {
        return support.one("SELECT * FROM storefronts WHERE id=? AND tenant_id=?", "Storefront", id, actor.tenantId());
    }

    public record StorefrontRequest(String name, String slug, String storeId, String storefrontType, String defaultLocale, List<String> supportedLocales) {}

    @Transactional
    public Map<String, Object> create(CatalogPrincipal actor, StorefrontRequest r) {
        String name = text(r.name(), "name", 160);
        String slug = r.slug() == null ? "" : r.slug().toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")) throw bad("slug must be 1-63 lowercase letters, digits or hyphens (it becomes the subdomain).");
        ContentValidator.slug(slug);
        String type = r.storefrontType() == null ? "RETAIL" : r.storefrontType();
        if (!Set.of("RETAIL", "RESTAURANT").contains(type)) throw bad("storefrontType must be RETAIL or RESTAURANT.");
        String store = storeId(r.storeId());
        if ("*".equals(store)) store = "default";
        String locale = r.defaultLocale() == null ? "en" : r.defaultLocale();
        if (!locale.matches("[a-z]{2}(-[A-Z]{2})?")) throw bad("defaultLocale must look like 'en' or 'en-US'.");
        Set<String> locales = new TreeSet<>();
        locales.add(locale);
        if (r.supportedLocales() != null) for (String l : r.supportedLocales()) { if (!l.matches("[a-z]{2}(-[A-Z]{2})?")) throw bad("Invalid locale " + l + "."); locales.add(l); }
        if (locales.size() > 10) throw bad("At most 10 locales.");
        try {
            jdbc.update("INSERT INTO storefronts(tenant_id, store_id, name, slug, storefront_type, default_locale, supported_locales, created_by, updated_by) VALUES (?,?,?,?,?,?,?,?,?)",
                    actor.tenantId(), store, name, slug, type, locale, String.join(",", locales), actor.userId(), actor.userId());
        } catch (DuplicateKeyException e) {
            throw conflict("That slug or store id is already used.");
        }
        long id = jdbc.queryForObject("SELECT id FROM storefronts WHERE slug=?", Long.class, slug);
        for (String[] d : new String[][]{{"HOMEPAGE", "Homepage"}, {"THEME", "Theme & branding"}, {"NAVIGATION", "Navigation"}, {"SETTINGS", "Storefront settings"}}) {
            long doc = insertDocument(actor, id, d[0], "", d[1]);
            JsonNode starter = validator.validate(d[0], mapper.createObjectNode(), refs(actor.tenantId(), id));
            insertVersion(actor, doc, 1, starter, "Initial draft", null);
        }
        audit(actor, id, "STOREFRONT", id, "CREATE", slug + " (" + type + ")");
        return get(actor, id);
    }

    @Transactional
    public Map<String, Object> updateName(CatalogPrincipal actor, long id, String name) {
        get(actor, id);
        jdbc.update("UPDATE storefronts SET name=?, version=version+1, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", text(name, "name", 160), actor.userId(), id, actor.tenantId());
        audit(actor, id, "STOREFRONT", id, "RENAME", name);
        return get(actor, id);
    }

    private long insertDocument(CatalogPrincipal actor, long storefrontId, String type, String slug, String title) {
        jdbc.update("INSERT INTO storefront_documents(tenant_id, storefront_id, doc_type, slug, title, created_by, updated_by) VALUES (?,?,?,?,?,?,?)",
                actor.tenantId(), storefrontId, type, slug, title, actor.userId(), actor.userId());
        return jdbc.queryForObject("SELECT id FROM storefront_documents WHERE storefront_id=? AND doc_type=? AND slug=?", Long.class, storefrontId, type, slug);
    }

    private long insertVersion(CatalogPrincipal actor, long docId, int no, JsonNode content, String note, Long source) {
        String json = json(content);
        jdbc.update("INSERT INTO storefront_versions(tenant_id, document_id, version_no, status, content, content_hash, change_note, source_version_id, created_by, updated_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                actor.tenantId(), docId, no, "DRAFT", json, sha(json), note, source, actor.userId(), actor.userId());
        return jdbc.queryForObject("SELECT id FROM storefront_versions WHERE document_id=? AND version_no=?", Long.class, docId, no);
    }

    ContentValidator.Refs refs(UUID tenant, long storefrontId) {
        return new ContentValidator.Refs(
                id -> count("SELECT COUNT(*) FROM storefront_media WHERE id=? AND tenant_id=? AND storefront_id=?", id, tenant, storefrontId) > 0,
                id -> count("SELECT COUNT(*) FROM product WHERE id=? AND tenant_id=?", id, tenant) > 0);
    }

    private int count(String sql, Object... args) { Integer n = jdbc.queryForObject(sql, Integer.class, args); return n == null ? 0 : n; }

    // ---- documents -----------------------------------------------------------------------------------------------

    public List<Map<String, Object>> documents(CatalogPrincipal actor, long storefrontId) {
        get(actor, storefrontId);
        List<Map<String, Object>> docs = support.rows("SELECT * FROM storefront_documents WHERE storefront_id=? AND tenant_id=? ORDER BY CASE doc_type WHEN 'HOMEPAGE' THEN 0 WHEN 'THEME' THEN 1 WHEN 'NAVIGATION' THEN 2 WHEN 'SETTINGS' THEN 3 ELSE 4 END, slug",
                storefrontId, actor.tenantId());
        for (Map<String, Object> d : docs) {
            long id = ((Number) d.get("id")).longValue();
            Map<String, Object> latest = support.rows("SELECT id, version_no, status, scheduled_publish_at, updated_at FROM storefront_versions WHERE document_id=? AND tenant_id=? ORDER BY version_no DESC LIMIT 1", id, actor.tenantId()).get(0);
            d.put("latestVersion", latest);
            Object pub = d.get("publishedVersionId");
            d.put("publishedVersionNo", pub == null ? null : jdbc.queryForObject("SELECT version_no FROM storefront_versions WHERE id=?", Integer.class, pub));
        }
        return docs;
    }

    Map<String, Object> document(CatalogPrincipal actor, long docId) {
        return support.one("SELECT * FROM storefront_documents WHERE id=? AND tenant_id=?", "Document", docId, actor.tenantId());
    }

    private Map<String, Object> lockDocument(CatalogPrincipal actor, long docId) {
        return support.one("SELECT * FROM storefront_documents WHERE id=? AND tenant_id=? FOR UPDATE", "Document", docId, actor.tenantId());
    }

    @Transactional
    public Map<String, Object> createLanding(CatalogPrincipal actor, long storefrontId, String slug, String title, JsonNode content) {
        get(actor, storefrontId);
        String s = ContentValidator.slug(slug);
        String t = text(title, "title", 160);
        JsonNode valid = validator.validate("LANDING", content == null ? mapper.createObjectNode() : content, refs(actor.tenantId(), storefrontId));
        long doc;
        try { doc = insertDocument(actor, storefrontId, "LANDING", s, t); }
        catch (DuplicateKeyException e) { throw conflict("A landing page with slug '" + s + "' already exists in this storefront."); }
        insertVersion(actor, doc, 1, valid, "Initial draft", null);
        audit(actor, storefrontId, "DOCUMENT", doc, "CREATE_LANDING", s);
        return document(actor, doc);
    }

    @Transactional
    public Map<String, Object> rename(CatalogPrincipal actor, long docId, String title) {
        Map<String, Object> d = lockDocument(actor, docId);
        jdbc.update("UPDATE storefront_documents SET title=?, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", text(title, "title", 160), actor.userId(), docId, actor.tenantId());
        audit(actor, ((Number) d.get("storefrontId")).longValue(), "DOCUMENT", docId, "RENAME", title);
        return document(actor, docId);
    }

    // ---- versions ------------------------------------------------------------------------------------------------

    public List<Map<String, Object>> versions(CatalogPrincipal actor, long docId) {
        document(actor, docId);
        return support.rows("SELECT id, document_id, version_no, status, change_note, source_version_id, scheduled_publish_at, submitted_by, submitted_at, approved_by, approved_at, published_by, published_at, created_by, created_at, updated_at "
                + "FROM storefront_versions WHERE document_id=? AND tenant_id=? ORDER BY version_no DESC", docId, actor.tenantId());
    }

    public Map<String, Object> version(CatalogPrincipal actor, long docId, long versionId) {
        document(actor, docId);
        Map<String, Object> v = support.one("SELECT * FROM storefront_versions WHERE id=? AND document_id=? AND tenant_id=?", "Version", versionId, docId, actor.tenantId());
        v.put("content", parse((String) v.get("content")));
        v.put("approvals", support.rows("SELECT * FROM storefront_approval_history WHERE version_id=? AND tenant_id=? ORDER BY id", versionId, actor.tenantId()));
        return v;
    }

    /** Saves editor content. Edits only ever touch the open DRAFT; if none exists a new DRAFT version is started. */
    @Transactional
    public Map<String, Object> saveDraft(CatalogPrincipal actor, long docId, JsonNode content, String note) {
        Map<String, Object> doc = lockDocument(actor, docId);
        long storefrontId = ((Number) doc.get("storefrontId")).longValue();
        JsonNode valid = validator.validate((String) doc.get("docType"), content, refs(actor.tenantId(), storefrontId));
        Map<String, Object> latest = support.rows("SELECT * FROM storefront_versions WHERE document_id=? AND tenant_id=? ORDER BY version_no DESC LIMIT 1 FOR UPDATE", docId, actor.tenantId()).get(0);
        String status = (String) latest.get("status");
        String json = json(valid);
        long vid;
        if (status.equals("DRAFT")) {
            vid = ((Number) latest.get("id")).longValue();
            int updated = jdbc.update("UPDATE storefront_versions SET content=?, content_hash=?, change_note=COALESCE(?, change_note), updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='DRAFT'",
                    json, sha(json), note, actor.userId(), vid, actor.tenantId());
            if (updated != 1) throw conflict("The draft changed state while saving; reload.");
        } else if (status.equals("IN_REVIEW") || status.equals("APPROVED")) {
            throw conflict("Version " + latest.get("versionNo") + " is " + status + ". Withdraw it before editing.");
        } else {
            vid = insertVersion(actor, docId, ((Number) latest.get("versionNo")).intValue() + 1, valid, note, ((Number) latest.get("id")).longValue());
        }
        audit(actor, storefrontId, "VERSION", vid, "SAVE_DRAFT", note);
        return version(actor, docId, vid);
    }

    private Map<String, Object> lockVersion(CatalogPrincipal actor, long docId, long versionId) {
        return support.one("SELECT * FROM storefront_versions WHERE id=? AND document_id=? AND tenant_id=? FOR UPDATE", "Version", versionId, docId, actor.tenantId());
    }

    private void history(CatalogPrincipal actor, long versionId, String action, String note) {
        jdbc.update("INSERT INTO storefront_approval_history(tenant_id, version_id, action, actor_id, note) VALUES (?,?,?,?,?)", actor.tenantId(), versionId, action, actor.userId(), note);
    }

    private void expect(Map<String, Object> v, String... statuses) {
        if (!List.of(statuses).contains(v.get("status"))) throw conflict("Version is " + v.get("status") + "; this action needs " + String.join(" or ", statuses) + ".");
    }

    @Transactional
    public Map<String, Object> submit(CatalogPrincipal actor, long docId, long versionId, String note) {
        Map<String, Object> doc = lockDocument(actor, docId);
        Map<String, Object> v = lockVersion(actor, docId, versionId);
        expect(v, "DRAFT");
        jdbc.update("UPDATE storefront_versions SET status='IN_REVIEW', submitted_by=?, submitted_at=CURRENT_TIMESTAMP, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", actor.userId(), actor.userId(), versionId, actor.tenantId());
        history(actor, versionId, "SUBMIT", note);
        audit(actor, ((Number) doc.get("storefrontId")).longValue(), "VERSION", versionId, "SUBMIT", note);
        return version(actor, docId, versionId);
    }

    @Transactional
    public Map<String, Object> decide(CatalogPrincipal actor, long docId, long versionId, boolean approve, String note) {
        Map<String, Object> doc = lockDocument(actor, docId);
        Map<String, Object> v = lockVersion(actor, docId, versionId);
        expect(v, "IN_REVIEW");
        if (v.get("submittedBy") != null && ((Number) v.get("submittedBy")).longValue() == actor.userId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot review a version you submitted.");
        if (!approve && (note == null || note.isBlank())) throw bad("A reason is required to send a version back.");
        if (approve) jdbc.update("UPDATE storefront_versions SET status='APPROVED', approved_by=?, approved_at=CURRENT_TIMESTAMP, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", actor.userId(), actor.userId(), versionId, actor.tenantId());
        else jdbc.update("UPDATE storefront_versions SET status='DRAFT', submitted_by=NULL, submitted_at=NULL, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", actor.userId(), versionId, actor.tenantId());
        history(actor, versionId, approve ? "APPROVE" : "REJECT", note);
        audit(actor, ((Number) doc.get("storefrontId")).longValue(), "VERSION", versionId, approve ? "APPROVE" : "REJECT", note);
        return version(actor, docId, versionId);
    }

    @Transactional
    public Map<String, Object> withdraw(CatalogPrincipal actor, long docId, long versionId, String note) {
        Map<String, Object> doc = lockDocument(actor, docId);
        Map<String, Object> v = lockVersion(actor, docId, versionId);
        expect(v, "IN_REVIEW", "APPROVED");
        jdbc.update("UPDATE storefront_versions SET status='DRAFT', submitted_by=NULL, submitted_at=NULL, approved_by=NULL, approved_at=NULL, scheduled_publish_at=NULL, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", actor.userId(), versionId, actor.tenantId());
        history(actor, versionId, "WITHDRAW", note);
        audit(actor, ((Number) doc.get("storefrontId")).longValue(), "VERSION", versionId, "WITHDRAW", note);
        return version(actor, docId, versionId);
    }

    /** Publishes an APPROVED version now, or schedules it when {@code at} is in the future. */
    @Transactional
    public Map<String, Object> publish(CatalogPrincipal actor, long docId, long versionId, OffsetDateTime at, String note) {
        Map<String, Object> doc = lockDocument(actor, docId);
        Map<String, Object> v = lockVersion(actor, docId, versionId);
        expect(v, "APPROVED");
        long storefrontId = ((Number) doc.get("storefrontId")).longValue();
        if (at != null && at.isAfter(OffsetDateTime.now().plusSeconds(30))) {
            jdbc.update("UPDATE storefront_versions SET scheduled_publish_at=?, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", at, actor.userId(), versionId, actor.tenantId());
            history(actor, versionId, "SCHEDULE", at.toString());
            audit(actor, storefrontId, "VERSION", versionId, "SCHEDULE", at.toString());
        } else {
            goLive(actor, doc, versionId, "PUBLISH", note);
        }
        return version(actor, docId, versionId);
    }

    @Transactional
    public Map<String, Object> cancelSchedule(CatalogPrincipal actor, long docId, long versionId) {
        Map<String, Object> doc = lockDocument(actor, docId);
        Map<String, Object> v = lockVersion(actor, docId, versionId);
        expect(v, "APPROVED");
        if (v.get("scheduledPublishAt") == null) throw conflict("Version is not scheduled.");
        jdbc.update("UPDATE storefront_versions SET scheduled_publish_at=NULL, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", actor.userId(), versionId, actor.tenantId());
        history(actor, versionId, "CANCEL_SCHEDULE", null);
        audit(actor, ((Number) doc.get("storefrontId")).longValue(), "VERSION", versionId, "CANCEL_SCHEDULE", null);
        return version(actor, docId, versionId);
    }

    private void goLive(CatalogPrincipal actor, Map<String, Object> doc, long versionId, String action, String note) {
        long docId = ((Number) doc.get("id")).longValue();
        jdbc.update("UPDATE storefront_versions SET status='ARCHIVED', updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE document_id=? AND tenant_id=? AND status='PUBLISHED' AND id<>?", actor.userId(), docId, actor.tenantId(), versionId);
        jdbc.update("UPDATE storefront_versions SET status='PUBLISHED', published_by=?, published_at=CURRENT_TIMESTAMP, scheduled_publish_at=NULL, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", actor.userId(), actor.userId(), versionId, actor.tenantId());
        jdbc.update("UPDATE storefront_documents SET published_version_id=?, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", versionId, actor.userId(), docId, actor.tenantId());
        history(actor, versionId, action, note);
        audit(actor, ((Number) doc.get("storefrontId")).longValue(), "VERSION", versionId, action, note);
    }

    /** Takes a landing page offline. The homepage, theme, navigation and settings always keep a published version once they have one. */
    @Transactional
    public Map<String, Object> unpublish(CatalogPrincipal actor, long docId) {
        Map<String, Object> doc = lockDocument(actor, docId);
        if (!"LANDING".equals(doc.get("docType"))) throw conflict("Only landing pages can be unpublished; publish a new version or roll back instead.");
        if (doc.get("publishedVersionId") == null) throw conflict("The page is not published.");
        long vid = ((Number) doc.get("publishedVersionId")).longValue();
        jdbc.update("UPDATE storefront_versions SET status='ARCHIVED', updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", actor.userId(), vid, actor.tenantId());
        jdbc.update("UPDATE storefront_documents SET published_version_id=NULL, updated_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", actor.userId(), docId, actor.tenantId());
        history(actor, vid, "UNPUBLISH", null);
        audit(actor, ((Number) doc.get("storefrontId")).longValue(), "VERSION", vid, "UNPUBLISH", null);
        return document(actor, docId);
    }

    /** Restores an earlier published/archived version as a new, immediately live version. Open drafts are left untouched. */
    @Transactional
    public Map<String, Object> rollback(CatalogPrincipal actor, long docId, long targetVersionId, String note) {
        Map<String, Object> doc = lockDocument(actor, docId);
        Map<String, Object> target = lockVersion(actor, docId, targetVersionId);
        if (!"ARCHIVED".equals(target.get("status"))) throw conflict("Only an archived (previously live) version can be restored.");
        long storefrontId = ((Number) doc.get("storefrontId")).longValue();
        // Re-validate: media or products referenced by the old content may have been removed since.
        JsonNode content = validator.validate((String) doc.get("docType"), parse((String) target.get("content")), refs(actor.tenantId(), storefrontId));
        int next = jdbc.queryForObject("SELECT MAX(version_no) FROM storefront_versions WHERE document_id=?", Integer.class, docId) + 1;
        long vid = insertVersion(actor, docId, next, content, note == null ? "Rollback to v" + target.get("versionNo") : note, targetVersionId);
        jdbc.update("UPDATE storefront_versions SET approved_by=?, approved_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", actor.userId(), vid, actor.tenantId());
        goLive(actor, doc, vid, "ROLLBACK", "to version " + target.get("versionNo"));
        return version(actor, docId, vid);
    }

    /** Path-level difference between two versions of the same document. */
    public Map<String, Object> compare(CatalogPrincipal actor, long docId, long fromId, long toId) {
        Map<String, Object> a = version(actor, docId, fromId), b = version(actor, docId, toId);
        Map<String, String> left = new TreeMap<>(), right = new TreeMap<>();
        flatten("", (JsonNode) a.get("content"), left);
        flatten("", (JsonNode) b.get("content"), right);
        List<Map<String, Object>> changes = new ArrayList<>();
        Set<String> keys = new TreeSet<>(left.keySet());
        keys.addAll(right.keySet());
        for (String k : keys) {
            String x = left.get(k), y = right.get(k);
            if (Objects.equals(x, y)) continue;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("path", k); c.put("before", x); c.put("after", y);
            c.put("change", x == null ? "ADDED" : y == null ? "REMOVED" : "CHANGED");
            changes.add(c);
        }
        return Map.of("from", a.get("versionNo"), "to", b.get("versionNo"), "changes", changes);
    }

    private static void flatten(String path, JsonNode n, Map<String, String> out) {
        if (n == null || n.isNull()) return;
        if (n.isObject()) n.fields().forEachRemaining(e -> flatten(path.isEmpty() ? e.getKey() : path + "." + e.getKey(), e.getValue(), out));
        else if (n.isArray()) for (int i = 0; i < n.size(); i++) flatten(path + "[" + i + "]", n.get(i), out);
        else out.put(path, n.asText());
    }

    /** Publishes approved versions whose schedule has arrived. Each version is its own transaction. */
    @Scheduled(fixedDelayString = "${storefront.scheduler.delay-ms:30000}")
    public void publishDue() {
        List<Map<String, Object>> due;
        try {
            due = jdbc.queryForList("SELECT id, document_id, tenant_id FROM storefront_versions WHERE status='APPROVED' AND scheduled_publish_at IS NOT NULL AND scheduled_publish_at <= CURRENT_TIMESTAMP ORDER BY scheduled_publish_at LIMIT 100");
        } catch (RuntimeException e) { return; }
        for (Map<String, Object> row : due) {
            try {
                tx.executeWithoutResult(s -> {
                    UUID tenant = (UUID) row.get("tenant_id");
                    long docId = ((Number) row.get("document_id")).longValue(), vid = ((Number) row.get("id")).longValue();
                    Map<String, Object> v = jdbc.queryForList("SELECT * FROM storefront_versions WHERE id=? AND status='APPROVED' AND scheduled_publish_at IS NOT NULL FOR UPDATE", vid).stream().findFirst().orElse(null);
                    if (v == null) return;
                    CatalogPrincipal system = new CatalogPrincipal(((Number) v.get("approved_by")).longValue(), "scheduler@system", "SYSTEM", tenant, "ECOMMERCE_ADMIN", Set.of("*"));
                    Map<String, Object> doc = jdbc.queryForList("SELECT * FROM storefront_documents WHERE id=? FOR UPDATE", docId).get(0);
                    Map<String, Object> camelDoc = PricingSupport.camel(doc);
                    goLive(system, camelDoc, vid, "PUBLISH_SCHEDULED", null);
                });
            } catch (RuntimeException e) { log.warn("Scheduled version {} could not be published: {}", row.get("id"), e.getMessage()); }
        }
    }

    public int publishDueNow() {
        int before = count("SELECT COUNT(*) FROM storefront_versions WHERE status='PUBLISHED'");
        publishDue();
        return count("SELECT COUNT(*) FROM storefront_versions WHERE status='PUBLISHED'") - before;
    }

    // ---- dashboard -----------------------------------------------------------------------------------------------

    public Map<String, Object> dashboard(CatalogPrincipal actor, long storefrontId) {
        Map<String, Object> sf = get(actor, storefrontId);
        UUID t = actor.tenantId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("storefront", sf);
        out.put("publishedPages", count("SELECT COUNT(*) FROM storefront_documents WHERE tenant_id=? AND storefront_id=? AND doc_type IN ('HOMEPAGE','LANDING') AND published_version_id IS NOT NULL", t, storefrontId));
        out.put("landingPages", count("SELECT COUNT(*) FROM storefront_documents WHERE tenant_id=? AND storefront_id=? AND doc_type='LANDING'", t, storefrontId));
        out.put("pendingReview", count("SELECT COUNT(*) FROM storefront_versions v JOIN storefront_documents d ON d.id=v.document_id WHERE d.tenant_id=? AND d.storefront_id=? AND v.status='IN_REVIEW'", t, storefrontId));
        out.put("awaitingPublish", count("SELECT COUNT(*) FROM storefront_versions v JOIN storefront_documents d ON d.id=v.document_id WHERE d.tenant_id=? AND d.storefront_id=? AND v.status='APPROVED'", t, storefrontId));
        out.put("scheduled", count("SELECT COUNT(*) FROM storefront_versions v JOIN storefront_documents d ON d.id=v.document_id WHERE d.tenant_id=? AND d.storefront_id=? AND v.status='APPROVED' AND v.scheduled_publish_at IS NOT NULL", t, storefrontId));
        out.put("activeBanners", count("SELECT COUNT(*) FROM storefront_banners WHERE tenant_id=? AND storefront_id=? AND active=TRUE", t, storefrontId));
        out.put("mediaFiles", count("SELECT COUNT(*) FROM storefront_media WHERE tenant_id=? AND storefront_id=?", t, storefrontId));
        out.put("verifiedDomains", count("SELECT COUNT(*) FROM storefront_domains WHERE tenant_id=? AND storefront_id=? AND verification_status='VERIFIED'", t, storefrontId));
        out.put("pendingDomains", count("SELECT COUNT(*) FROM storefront_domains WHERE tenant_id=? AND storefront_id=? AND verification_status<>'VERIFIED'", t, storefrontId));
        return out;
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    JsonNode parse(String json) {
        try { return mapper.readTree(json); } catch (Exception e) { throw new IllegalStateException("Stored content is corrupt.", e); }
    }

    private String json(JsonNode n) {
        try { return mapper.writeValueAsString(n); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    static String sha(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
