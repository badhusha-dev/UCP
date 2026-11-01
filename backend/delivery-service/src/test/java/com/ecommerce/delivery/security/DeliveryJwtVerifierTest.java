package com.ecommerce.delivery.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryJwtVerifierTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private final DeliveryJwtVerifier verifier = new DeliveryJwtVerifier();

    @BeforeEach
    void configureVerifier() {
        ReflectionTestUtils.setField(verifier, "secret", SECRET);
        ReflectionTestUtils.setField(verifier, "issuer", "universal-commerce");
        ReflectionTestUtils.setField(verifier, "audience", "commerce-api");
    }

    @Test
    void acceptsOnlyScopedDeliveryApplicationTokens() {
        assertTrue(verifier.isValidDeliveryToken(token("DELIVERY_OPS", true)));
        assertFalse(verifier.isValidDeliveryToken(token("INVENTORY", true)));
        assertFalse(verifier.isValidDeliveryToken(token("DELIVERY_OPS", false)));
    }

    private String token(String applicationCode, boolean includeTenant) {
        var builder = Jwts.builder().setIssuer("universal-commerce").setAudience("commerce-api")
                .setSubject("driver@example.test").setId(UUID.randomUUID().toString())
                .claim("id", 42L).claim("tenant_role", "DELIVERY_PARTNER")
                .claim("application_code", applicationCode).setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000));
        if (includeTenant) builder.claim("tenant_id", UUID.randomUUID().toString());
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
    }
}
