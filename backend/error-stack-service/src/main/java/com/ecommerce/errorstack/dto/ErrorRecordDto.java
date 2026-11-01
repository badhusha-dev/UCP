package com.ecommerce.errorstack.dto;

import com.ecommerce.errorstack.model.ErrorSeverity;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorRecordDto {
    private Long id;
    private Long groupId;
    private String fingerprint;
    private String serviceName;
    private String version;
    private String environment;
    private String releaseTag;
    private String errorType;
    private String message;
    private String stackTrace;
    private String exceptionChain;
    private String requestMethod;
    private String requestUri;
    private Integer responseStatus;
    private String traceId;
    private String correlationId;
    private Long userId;
    private String userRole;
    private String browser;
    private String os;
    private String frontendRoute;
    private ErrorSeverity severity;
    private Map<String, Object> metadata;
    private List<BreadcrumbDto> breadcrumbs;
    private OffsetDateTime createdAt;
}
