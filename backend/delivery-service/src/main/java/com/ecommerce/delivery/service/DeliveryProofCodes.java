package com.ecommerce.delivery.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * One-time delivery codes. Codes are random, stored only as a keyed hash bound to the delivery, and compared in constant time.
 * Without a configured secret the component refuses to issue or verify anything (fail closed).
 */
@Component
public class DeliveryProofCodes {
    private final SecureRandom random = new SecureRandom();
    @Value("${delivery.otp.pepper:}")
    private String pepper;

    public String newCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    public String hashCode(UUID deliveryId, String code) {
        return hmac("otp:" + deliveryId + ":" + code);
    }

    /** Keyed digest for request fingerprints that may contain a code, so idempotency records never expose it to guessing. */
    public String fingerprint(String material) {
        return hmac("fp:" + material);
    }

    public boolean matches(String expectedHash, UUID deliveryId, String suppliedCode) {
        byte[] expected = expectedHash.getBytes(StandardCharsets.UTF_8);
        byte[] actual = hashCode(deliveryId, suppliedCode).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private String hmac(String material) {
        if (!StringUtils.hasText(pepper) || pepper.length() < 16) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Proof verification is not configured.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not compute proof digest.", failure);
        }
    }
}
