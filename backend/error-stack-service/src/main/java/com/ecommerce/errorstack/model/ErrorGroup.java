package com.ecommerce.errorstack.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "error_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fingerprint", nullable = false, unique = true, length = 64)
    private String fingerprint;

    @Column(name = "service_name", nullable = false, length = 128)
    private String serviceName;

    @Column(name = "environment", nullable = false, length = 64)
    private String environment;

    @Column(name = "error_type", nullable = false, length = 256)
    private String errorType;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private ErrorSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ErrorStatus status;

    @Column(name = "assigned_to", length = 128)
    private String assignedTo;

    @Column(name = "occurrences_count", nullable = false)
    private Long occurrencesCount;

    @Column(name = "first_seen", nullable = false)
    private OffsetDateTime firstSeen;

    @Column(name = "last_seen", nullable = false)
    private OffsetDateTime lastSeen;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ErrorRecord> records = new ArrayList<>();
}
