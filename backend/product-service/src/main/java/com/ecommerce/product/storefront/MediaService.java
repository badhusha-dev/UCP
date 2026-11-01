package com.ecommerce.product.storefront;

import com.ecommerce.product.pricing.PricingSupport;
import com.ecommerce.product.security.CatalogPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import static com.ecommerce.product.pricing.PricingSupport.*;

/** Storefront images. Binary content lives in object storage behind {@link MediaStorage}; the database keeps metadata only. */
@Service
public class MediaService {
    /** Object-storage abstraction. The shipped implementation writes to a directory/volume; an S3 adapter can replace it without touching callers. */
    public interface MediaStorage {
        void put(String key, byte[] content) throws IOException;
        byte[] get(String key) throws IOException;
        void delete(String key) throws IOException;
    }

    @Component
    public static class LocalMediaStorage implements MediaStorage {
        private final Path root;

        public LocalMediaStorage(@Value("${storefront.media.dir:${java.io.tmpdir}/ucp-storefront-media}") String dir) { this.root = Paths.get(dir).toAbsolutePath().normalize(); }

        private Path resolve(String key) {
            Path p = root.resolve(key).normalize();
            if (!p.startsWith(root)) throw new IllegalArgumentException("Invalid storage key.");
            return p;
        }

        @Override public void put(String key, byte[] content) throws IOException {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), "up-", ".tmp");
            Files.write(tmp, content);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        @Override public byte[] get(String key) throws IOException { return Files.readAllBytes(resolve(key)); }
        @Override public void delete(String key) throws IOException { Files.deleteIfExists(resolve(key)); }
    }

    private record Limits(int minW, int maxW, int minH, int maxH, boolean square, Set<String> types) {}

    private static final Set<String> ALL = Set.of("image/png", "image/jpeg", "image/gif");
    private static final Map<String, Limits> LIMITS = Map.of(
            "LOGO", new Limits(64, 2000, 32, 1000, false, ALL),
            "FAVICON", new Limits(32, 512, 32, 512, true, Set.of("image/png")),
            "BANNER_DESKTOP", new Limits(1200, 4000, 200, 2000, false, ALL),
            "BANNER_MOBILE", new Limits(400, 1600, 200, 2400, false, ALL),
            "PRODUCT", new Limits(200, 4000, 200, 4000, false, ALL),
            "SOCIAL", new Limits(600, 4000, 315, 4000, false, ALL),
            "GENERAL", new Limits(50, 4000, 50, 4000, false, ALL));
    private static final long MAX_PIXELS = 25_000_000L;

    private final JdbcTemplate jdbc;
    private final PricingSupport support;
    private final MediaStorage storage;
    private final StorefrontService storefronts;

    @Value("${storefront.media.max-bytes:5242880}") private long maxBytes;

    public MediaService(JdbcTemplate jdbc, PricingSupport support, MediaStorage storage, StorefrontService storefronts) {
        this.jdbc = jdbc; this.support = support; this.storage = storage; this.storefronts = storefronts;
    }

    public record Upload(String publicId, String contentType, int width, int height, long size) {}

    @Transactional
    public Map<String, Object> upload(CatalogPrincipal actor, long storefrontId, String kind, String altText, String originalName, byte[] bytes) {
        storefronts.get(actor, storefrontId);
        Limits limits = LIMITS.get(kind);
        if (limits == null) throw bad("kind must be one of " + new TreeSet<>(LIMITS.keySet()) + ".");
        if (bytes == null || bytes.length == 0) throw bad("The file is empty.");
        if (bytes.length > maxBytes) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Images may be at most " + maxBytes / 1024 / 1024 + " MB.");
        String alt = ContentValidator.plain(altText, "altText", 255, true);
        String type = sniff(bytes);
        if (type == null) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PNG, JPEG and GIF images are accepted (SVG and other formats are refused).");
        if (!limits.types().contains(type)) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, kind + " images must be " + String.join(" or ", new TreeSet<>(limits.types())) + ".");
        int[] dim = dimensions(bytes);
        if (dim == null) throw bad("The file is not a readable image.");
        if ((long) dim[0] * dim[1] > MAX_PIXELS) throw bad("The image has too many pixels (max 25 megapixels).");
        if (dim[0] < limits.minW() || dim[0] > limits.maxW() || dim[1] < limits.minH() || dim[1] > limits.maxH())
            throw bad(kind + " images must be " + limits.minW() + "-" + limits.maxW() + " px wide and " + limits.minH() + "-" + limits.maxH() + " px high (got " + dim[0] + "x" + dim[1] + ").");
        if (limits.square() && dim[0] != dim[1]) throw bad("Favicons must be square.");
        // Decoding fully is the real proof the file is an image (truncated/forged files fail here).
        try { if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) throw bad("The file is not a readable image."); }
        catch (IOException e) { throw bad("The file is not a readable image."); }

        String publicId = UUID.randomUUID().toString();
        String ext = type.equals("image/png") ? "png" : type.equals("image/jpeg") ? "jpg" : "gif";
        String key = actor.tenantId() + "/" + storefrontId + "/" + publicId + "." + ext;
        try { storage.put(key, bytes); } catch (IOException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image storage is unavailable."); }
        try {
            jdbc.update("INSERT INTO storefront_media(tenant_id, storefront_id, public_id, kind, file_name, content_type, size_bytes, width, height, sha256, alt_text, storage_key, created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    actor.tenantId(), storefrontId, publicId, kind, safeName(originalName), type, bytes.length, dim[0], dim[1], sha(bytes), alt, key, actor.userId());
        } catch (RuntimeException e) {
            try { storage.delete(key); } catch (IOException ignored) { /* best effort */ }
            throw e;
        }
        storefronts.audit(actor, storefrontId, "MEDIA", publicId, "UPLOAD", kind + " " + dim[0] + "x" + dim[1] + " " + bytes.length + "B");
        return support.one("SELECT * FROM storefront_media WHERE public_id=? AND tenant_id=?", "Media", publicId, actor.tenantId());
    }

    public List<Map<String, Object>> list(CatalogPrincipal actor, long storefrontId, String kind) {
        storefronts.get(actor, storefrontId);
        if (kind == null || kind.isBlank()) return support.rows("SELECT * FROM storefront_media WHERE tenant_id=? AND storefront_id=? ORDER BY id DESC LIMIT 500", actor.tenantId(), storefrontId);
        return support.rows("SELECT * FROM storefront_media WHERE tenant_id=? AND storefront_id=? AND kind=? ORDER BY id DESC LIMIT 500", actor.tenantId(), storefrontId, kind);
    }

    @Transactional
    public void delete(CatalogPrincipal actor, long mediaId) {
        Map<String, Object> m = support.one("SELECT * FROM storefront_media WHERE id=? AND tenant_id=?", "Media", mediaId, actor.tenantId());
        Integer banners = jdbc.queryForObject("SELECT COUNT(*) FROM storefront_banners WHERE desktop_media_id=? OR mobile_media_id=?", Integer.class, mediaId, mediaId);
        if (banners != null && banners > 0) throw conflict("The image is used by a banner.");
        // Content references are JSON; scan every stored version for the numeric id after any *MediaId key.
        List<String> contents = jdbc.queryForList("SELECT content FROM storefront_versions WHERE tenant_id=?", String.class, actor.tenantId());
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"[A-Za-z]*MediaId\":" + mediaId + "(?![0-9])");
        if (contents.stream().anyMatch(c -> p.matcher(c).find())) throw conflict("The image is referenced by page, theme or SEO content (including older versions).");
        jdbc.update("DELETE FROM storefront_media WHERE id=? AND tenant_id=?", mediaId, actor.tenantId());
        try { storage.delete((String) m.get("storageKey")); } catch (IOException ignored) { /* orphan file is harmless */ }
        storefronts.audit(actor, ((Number) m.get("storefrontId")).longValue(), "MEDIA", m.get("publicId"), "DELETE", null);
    }

    /** Public delivery: any stored image by its unguessable id, served with a pinned content type. */
    public Optional<Map.Entry<String, byte[]>> read(String publicId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT content_type, storage_key FROM storefront_media WHERE public_id=?", publicId);
        if (rows.isEmpty()) return Optional.empty();
        try { return Optional.of(Map.entry((String) rows.get(0).get("content_type"), storage.get((String) rows.get(0).get("storage_key")))); }
        catch (IOException e) { return Optional.empty(); }
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    static String sniff(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "image/gif";
        return null;
    }

    /** Reads width/height from the header only, so a decompression bomb is rejected before any pixel is decoded. */
    static int[] dimensions(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader r = readers.next();
            try { r.setInput(in); return new int[]{r.getWidth(0), r.getHeight(0)}; } finally { r.dispose(); }
        } catch (IOException | RuntimeException e) { return null; }
    }

    static String safeName(String name) {
        String base = name == null ? "image" : name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isBlank() || base.startsWith(".")) base = "image";
        return base.substring(0, Math.min(base.length(), 100));
    }

    private static String sha(byte[] b) {
        try { StringBuilder sb = new StringBuilder(); for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) sb.append(String.format("%02x", x)); return sb.toString(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
