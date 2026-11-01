package com.ecommerce.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final String DEV_TEST_KEY =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.application-expiration:900000}")
    private Long applicationExpiration;

    @Value("${jwt.issuer:universal-commerce}")
    private String issuer;

    @Value("${jwt.audience:commerce-api}")
    private String audience;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret is not configured — set the JWT_SECRET environment variable");
        }
        if (secret.length() < 64) {
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 bytes (64 hex chars); generate with: openssl rand -hex 32");
        }
        if (DEV_TEST_KEY.equalsIgnoreCase(secret)) {
            log.warn("jwt.secret is the well-known development test key — " +
                     "replace it with a unique secret before deploying to production " +
                     "(openssl rand -hex 32)");
        }
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(Long id, String email, String name, String role) {
        return generateToken(id, email, name, role, Set.of());
    }

    public String generateToken(Long id, String email, String name, String role, Set<String> permissions) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        claims.put("name", name);
        claims.put("role", role);
        claims.put("email", email);
        claims.put("permissions", permissions);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .setIssuer(issuer)
                .setAudience(audience)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateApplicationToken(Long id, String email, String name, String tenantRole,
                                           String applicationCode, UUID tenantId, Set<String> permissions) {
        Date now = new Date();
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        claims.put("name", name);
        claims.put("role", tenantRole);
        claims.put("tenant_role", tenantRole);
        claims.put("email", email);
        claims.put("permissions", permissions);
        claims.put("tenant_id", tenantId.toString());
        claims.put("application_code", applicationCode);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + applicationExpiration))
                .setIssuer(issuer)
                .setAudience(audience)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
    }

    public boolean validateToken(String token) {
        try { return getClaims(token).getExpiration().after(new Date()); }
        catch (Exception ignored) { return false; }
    }

    public boolean validateApplicationToken(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.getExpiration().after(new Date())
                    && issuer.equals(claims.getIssuer())
                    && audience.equals(claims.getAudience())
                    && claims.get("tenant_id", String.class) != null
                    && claims.get("application_code", String.class) != null
                    && claims.get("tenant_role", String.class) != null
                    && claims.getId() != null;
        } catch (Exception ignored) { return false; }
    }

    public boolean validatePlatformToken(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.getExpiration() != null && claims.getExpiration().after(new Date())
                    && issuer.equals(claims.getIssuer())
                    && audience.equals(claims.getAudience())
                    && claims.get("id", Number.class) != null;
        } catch (Exception ignored) { return false; }
    }
}
