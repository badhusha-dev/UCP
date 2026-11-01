package com.ecommerce.user.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "revoked_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevokedToken {

    @Id
    @Column(name = "jti", length = 256)
    private String jti;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "revoked_at", nullable = false)
    private OffsetDateTime revokedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @PrePersist
    void prePersist() {
        if (revokedAt == null) revokedAt = OffsetDateTime.now();
    }
}
