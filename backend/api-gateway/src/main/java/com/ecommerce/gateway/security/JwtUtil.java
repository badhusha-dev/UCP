package com.ecommerce.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final String DEV_TEST_KEY =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Value("${jwt.secret}")
    private String secret;

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

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateApplicationToken(String token, String applicationCode) {
        try {
            Claims claims = getClaims(token);
            return claims.getExpiration() != null && claims.getExpiration().after(new Date())
                    && issuer.equals(claims.getIssuer())
                    && audience.equals(claims.getAudience())
                    && applicationCode.equals(claims.get("application_code", String.class))
                    && claims.get("tenant_id", String.class) != null
                    && claims.get("tenant_role", String.class) != null
                    && claims.getId() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validatePlatformToken(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.getExpiration() != null && claims.getExpiration().after(new Date())
                    && issuer.equals(claims.getIssuer())
                    && audience.equals(claims.getAudience())
                    && claims.get("id", Number.class) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
