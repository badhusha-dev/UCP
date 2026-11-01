package com.ecommerce.product.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class CatalogJwtVerifier {
    static final String APPLICATION_CODE = "ECOMMERCE_ADMIN";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.issuer:universal-commerce}")
    private String issuer;

    @Value("${jwt.audience:commerce-api}")
    private String audience;

    public Claims parse(String token) {
        return Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(secret.getBytes())).build()
                .parseClaimsJws(token).getBody();
    }

    public boolean isValidCatalogToken(String token) {
        try {
            Claims claims = parse(token);
            return claims.getExpiration() != null && claims.getExpiration().after(new Date())
                    && issuer.equals(claims.getIssuer())
                    && audience.equals(claims.getAudience())
                    && APPLICATION_CODE.equals(claims.get("application_code", String.class))
                    && claims.get("tenant_id", String.class) != null
                    && claims.get("tenant_role", String.class) != null
                    && claims.getId() != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
