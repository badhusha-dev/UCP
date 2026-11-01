package com.ecommerce.user.service;

import com.ecommerce.user.model.RevokedToken;
import com.ecommerce.user.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final RevokedTokenRepository repository;

    @Transactional
    public void revoke(String jti, Long userId, Date expiresAt) {
        if (jti == null) return;
        repository.save(RevokedToken.builder()
                .jti(jti)
                .userId(userId)
                .revokedAt(OffsetDateTime.now())
                .expiresAt(expiresAt.toInstant().atOffset(ZoneOffset.UTC))
                .build());
    }

    public boolean isRevoked(String jti) {
        if (jti == null) return false;
        return repository.existsById(jti);
    }

    @Scheduled(fixedDelayString = "${jwt.revocation-purge-interval-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        repository.deleteByExpiresAtBefore(OffsetDateTime.now());
    }
}
