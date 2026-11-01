package com.ecommerce.qa.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class TenantJwtVerifier {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.issuer:universal-commerce}")
    private String issuer;

    @Value("${jwt.audience:commerce-api}")
    private String audience;

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isValidTenantToken(String token) {
        try {
            Claims claims = parse(token);
            return claims.getExpiration() != null
                    && claims.getExpiration().after(new Date())
                    && issuer.equals(claims.getIssuer())
                    && audience.equals(claims.getAudience())
                    && claims.get("tenant_id", String.class) != null
                    && claims.get("id", Number.class) != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
