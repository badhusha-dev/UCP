package com.ecommerce.order.rma;

import com.ecommerce.order.admin.AdminSupport;
import com.ecommerce.order.admin.security.AdminPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.*;

/**
 * Customer evidence, inspection photographs and pickup proof. Files are private: there is no public URL, every download goes through
 * an authenticated, tenant- and store-checked endpoint. Uploads are accepted only as PNG, JPEG, GIF or PDF, verified by content (not by
 * the declared type or extension), size-limited and stored outside the database.
 */
@Service
public class AttachmentService {
    private final JdbcTemplate jdbc;
    private final AdminSupport support;
    private final Path root;

    @Value("${returns.attachments.max-bytes:5242880}") private long maxBytes;
    private static final long MAX_PIXELS = 25_000_000L;
    private static final int MAX_PER_RETURN = 20;

    public AttachmentService(JdbcTemplate jdbc, AdminSupport support, @Value("${returns.attachments.dir:${java.io.tmpdir}/ucp-return-evidence}") String dir) {
        this.jdbc = jdbc; this.support = support; this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    public Map<String, Object> upload(UUID tenant, long returnId, String kind, String fileName, byte[] bytes, String note, Long actorId, Long customerId) {
        if (!Set.of("CUSTOMER_EVIDENCE", "INSPECTION", "PICKUP_PROOF").contains(kind)) throw bad("kind must be CUSTOMER_EVIDENCE, INSPECTION or PICKUP_PROOF.");
        if (bytes == null || bytes.length == 0) throw bad("The file is empty.");
        if (bytes.length > maxBytes) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Files may be at most " + maxBytes / 1024 / 1024 + " MB.");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM return_attachments WHERE tenant_id=? AND return_id=?", Integer.class, tenant, returnId);
        if (count != null && count >= MAX_PER_RETURN) throw conflict("A return can have at most " + MAX_PER_RETURN + " attachments.");
        String type = sniff(bytes);
        if (type == null) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PNG, JPEG, GIF images and PDF documents are accepted.");
        if (!type.equals("application/pdf")) {
            int[] d = dimensions(bytes);
            if (d == null) throw bad("The file is not a readable image.");
            if ((long) d[0] * d[1] > MAX_PIXELS) throw bad("The image has too many pixels (max 25 megapixels).");
            try { if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) throw bad("The file is not a readable image."); } catch (IOException e) { throw bad("The file is not a readable image."); }
        }
        String n = note == null || note.isBlank() ? null : note.strip();
        if (n != null && (n.length() > 255 || n.matches(".*[<>].*"))) throw bad("note must be plain text of at most 255 characters.");
        String ext = type.equals("image/png") ? "png" : type.equals("image/jpeg") ? "jpg" : type.equals("image/gif") ? "gif" : "pdf";
        String key = tenant + "/" + returnId + "/" + UUID.randomUUID() + "." + ext;
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), "up-", ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Evidence storage is unavailable."); }
        jdbc.update("INSERT INTO return_attachments(tenant_id, return_id, kind, file_name, content_type, size_bytes, sha256, storage_key, note, uploaded_by_actor, uploaded_by_customer) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                tenant, returnId, kind, safeName(fileName), type, bytes.length, sha(bytes), key, n, actorId, customerId);
        return support.one("SELECT id, kind, file_name, content_type, size_bytes, note, created_at FROM return_attachments WHERE tenant_id=? AND storage_key=?", "Attachment", tenant, key);
    }

    public List<Map<String, Object>> list(UUID tenant, long returnId) {
        return support.rows("SELECT id, kind, file_name, content_type, size_bytes, note, uploaded_by_actor, uploaded_by_customer, created_at FROM return_attachments WHERE tenant_id=? AND return_id=? ORDER BY id", tenant, returnId);
    }

    public boolean hasCustomerEvidence(UUID tenant, long returnId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM return_attachments WHERE tenant_id=? AND return_id=? AND kind='CUSTOMER_EVIDENCE'", Integer.class, tenant, returnId);
        return n != null && n > 0;
    }

    public record Download(String contentType, String fileName, byte[] content) {}

    /** The caller has already authorised access to the owning return. */
    public Download download(UUID tenant, long returnId, long attachmentId) {
        Map<String, Object> a = support.one("SELECT * FROM return_attachments WHERE id=? AND tenant_id=? AND return_id=?", "Attachment", attachmentId, tenant, returnId);
        try { return new Download((String) a.get("contentType"), (String) a.get("fileName"), Files.readAllBytes(resolve((String) a.get("storageKey")))); }
        catch (IOException e) { throw notFound("Attachment"); }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Invalid storage key.");
        return p;
    }

    static String sniff(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "image/gif";
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return "application/pdf";
        return null;
    }

    static int[] dimensions(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader r = readers.next();
            try { r.setInput(in); return new int[]{r.getWidth(0), r.getHeight(0)}; } finally { r.dispose(); }
        } catch (IOException | RuntimeException e) { return null; }
    }

    static String safeName(String name) {
        String base = name == null ? "file" : name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isBlank() || base.startsWith(".")) base = "file";
        return base.substring(0, Math.min(base.length(), 100));
    }

    private static String sha(byte[] b) {
        try { StringBuilder sb = new StringBuilder(); for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) sb.append(String.format("%02x", x)); return sb.toString(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
