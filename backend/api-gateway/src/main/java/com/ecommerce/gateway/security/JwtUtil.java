package com.ecommerce.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.issuer:universal-commerce}")
    private String issuer;

    @Value("${jwt.audience:commerce-api}")
    private String audience;

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
