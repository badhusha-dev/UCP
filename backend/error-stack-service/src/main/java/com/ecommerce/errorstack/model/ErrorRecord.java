package com.ecommerce.errorstack.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "error_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ErrorGroup group;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "service_name", nullable = false, length = 128)
    private String serviceName;

    @Column(name = "version", length = 64)
    private String version;

    @Column(name = "environment", nullable = false, length = 64)
    private String environment;

    @Column(name = "release_tag", length = 64)
    private String releaseTag;

    @Column(name = "error_type", nullable = false, length = 256)
    private String errorType;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "exception_chain", columnDefinition = "TEXT")
    private String exceptionChain;

    @Column(name = "request_method", length = 16)
    private String requestMethod;

    @Column(name = "request_uri", length = 1024)
    private String requestUri;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_role", length = 64)
    private String userRole;

    @Column(name = "browser", length = 128)
    private String browser;

    @Column(name = "os", length = 128)
    private String os;

    @Column(name = "frontend_route", length = 512)
    private String frontendRoute;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private ErrorSeverity severity;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "breadcrumbs_json", columnDefinition = "TEXT")
    private String breadcrumbsJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
