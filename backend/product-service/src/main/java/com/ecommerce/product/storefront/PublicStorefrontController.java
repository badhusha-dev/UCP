package com.ecommerce.product.storefront;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Anonymous, read-only storefront delivery of PUBLISHED content. See {@link PublicStorefrontService} for the exposure rules. */
@RestController
@RequestMapping("/api/products/public/storefront")
public class PublicStorefrontController {
    private final PublicStorefrontService service;
    private final MediaService media;

    public PublicStorefrontController(PublicStorefrontService service, MediaService media) { this.service = service; this.media = media; }

    @GetMapping("/resolve")
    public Map<String, Object> resolve(@RequestParam String host) { return service.resolveHost(host); }

    @GetMapping("/{slug}")
    public ResponseEntity<Map<String, Object>> site(@PathVariable String slug) { return cached(service.site(slug)); }

    @GetMapping("/{slug}/home")
    public ResponseEntity<Map<String, Object>> home(@PathVariable String slug) { return cached(service.page(slug, "HOMEPAGE", "")); }

    @GetMapping("/{slug}/pages/{pageSlug}")
    public ResponseEntity<Map<String, Object>> landing(@PathVariable String slug, @PathVariable String pageSlug) { return cached(service.page(slug, "LANDING", pageSlug)); }

    @GetMapping("/{slug}/banners")
    public ResponseEntity<List<Map<String, Object>>> banners(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic()).body(service.banners(slug));
    }

    @GetMapping(value = "/{slug}/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap(@PathVariable String slug) { return service.sitemap(slug); }

    @GetMapping(value = "/{slug}/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots(@PathVariable String slug) { return service.robots(slug); }

    @GetMapping("/media/{publicId}")
    public ResponseEntity<byte[]> media(@PathVariable String publicId) {
        if (!publicId.matches("[0-9a-fA-F-]{36}")) throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        var found = media.read(publicId.toLowerCase());
        if (found.isEmpty()) throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(found.get().getKey()))
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "default-src 'none'; sandbox")
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic()).body(found.get().getValue());
    }

    /** Short public caching: published content changes only on an explicit publish, and a minute of staleness is acceptable. */
    private static <T> ResponseEntity<T> cached(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic()).body(body);
    }
}
