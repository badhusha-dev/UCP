package com.ecommerce.errorstack.service;

import com.ecommerce.errorstack.dto.*;
import com.ecommerce.errorstack.model.*;
import com.ecommerce.errorstack.repository.ErrorGroupRepository;
import com.ecommerce.errorstack.repository.ErrorRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ErrorStackService {

    private final ErrorGroupRepository groupRepository;
    private final ErrorRecordRepository recordRepository;
    private final ErrorSanitizer sanitizer;
    private final FingerprintGenerator fingerprintGenerator;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ErrorRecordDto ingest(ErrorIngestRequest req) {
        String service = req.getServiceName() != null ? req.getServiceName().trim() : "unknown-service";

        // 1. Rate limiter defense
        if (!rateLimiterService.tryAcquire(service)) {
            log.warn("Error ingestion rate limit exceeded for service [{}] - dropping overflow event", service);
            return null;
        }

        // 2. Sensitive data redaction
        String sanitizedMsg = sanitizer.sanitizeMessage(req.getMessage());
        String sanitizedStack = sanitizer.sanitizeStackTrace(req.getStackTrace());
        String sanitizedChain = sanitizer.sanitizeExceptionChain(req.getExceptionChain());
        String sanitizedUri = sanitizer.sanitizeUri(req.getRequestUri());

        String env = (req.getEnvironment() != null && !req.getEnvironment().isBlank())
                ? req.getEnvironment().trim() : "production";
        String errorType = (req.getErrorType() != null && !req.getErrorType().isBlank())
                ? req.getErrorType().trim() : "UnknownException";
        ErrorSeverity severity = req.getSeverity() != null ? req.getSeverity() : ErrorSeverity.ERROR;
        OffsetDateTime now = req.getTimestamp() != null ? req.getTimestamp() : OffsetDateTime.now();

        // 3. Stable fingerprint calculation
        String fingerprint = fingerprintGenerator.generateFingerprint(
                service, errorType, sanitizedMsg, sanitizedStack);

        // 4. Find or create group
        ErrorGroup group = groupRepository.findByFingerprint(fingerprint)
                .map(existing -> {
                    existing.setOccurrencesCount(existing.getOccurrencesCount() + 1);
                    existing.setLastSeen(now);
                    // If error was marked RESOLVED and recurs, reopen as NEW (regression alert)
                    if (existing.getStatus() == ErrorStatus.RESOLVED) {
                        existing.setStatus(ErrorStatus.NEW);
                    }
                    if (req.getSeverity() != null && req.getSeverity().ordinal() < existing.getSeverity().ordinal()) {
                        existing.setSeverity(req.getSeverity()); // escalate severity if more severe
                    }
                    return groupRepository.save(existing);
                })
                .orElseGet(() -> {
                    ErrorGroup newGroup = ErrorGroup.builder()
                            .fingerprint(fingerprint)
                            .serviceName(service)
                            .environment(env)
                            .errorType(errorType)
                            .message(sanitizedMsg)
                            .severity(severity)
                            .status(ErrorStatus.NEW)
                            .occurrencesCount(1L)
                            .firstSeen(now)
                            .lastSeen(now)
                            .build();
                    return groupRepository.save(newGroup);
                });

        // 5. Serialize metadata and breadcrumbs
        String metadataJson = null;
        if (req.getMetadata() != null && !req.getMetadata().isEmpty()) {
            try {
                metadataJson = objectMapper.writeValueAsString(req.getMetadata());
            } catch (JsonProcessingException e) {
                log.debug("Failed to serialize metadata: {}", e.getMessage());
            }
        }

        String breadcrumbsJson = null;
        if (req.getBreadcrumbs() != null && !req.getBreadcrumbs().isEmpty()) {
            try {
                // Keep max 50 breadcrumbs
                List<BreadcrumbDto> trimmed = req.getBreadcrumbs();
                if (trimmed.size() > 50) {
                    trimmed = trimmed.subList(trimmed.size() - 50, trimmed.size());
                }
                breadcrumbsJson = objectMapper.writeValueAsString(trimmed);
            } catch (JsonProcessingException e) {
                log.debug("Failed to serialize breadcrumbs: {}", e.getMessage());
            }
        }

        // 6. Persist individual record
        ErrorRecord record = ErrorRecord.builder()
                .group(group)
                .fingerprint(fingerprint)
                .serviceName(service)
                .version(req.getVersion())
                .environment(env)
                .releaseTag(req.getReleaseTag())
                .errorType(errorType)
                .message(sanitizedMsg)
                .stackTrace(sanitizedStack)
                .exceptionChain(sanitizedChain)
                .requestMethod(req.getRequestMethod())
                .requestUri(sanitizedUri)
                .responseStatus(req.getResponseStatus())
                .traceId(req.getTraceId())
                .correlationId(req.getCorrelationId())
                .userId(req.getUserId())
                .userRole(req.getUserRole())
                .browser(req.getBrowser())
                .os(req.getOs())
                .frontendRoute(req.getFrontendRoute())
                .severity(severity)
                .metadataJson(metadataJson)
                .breadcrumbsJson(breadcrumbsJson)
                .createdAt(now)
                .build();

        record = recordRepository.save(record);
        log.info("Ingested application error: service=[{}], type=[{}], fingerprint=[{}], group Occurrences=[{}]",
                service, errorType, fingerprint, group.getOccurrencesCount());

        return toRecordDto(record);
    }

    @Transactional(readOnly = true)
    public Page<ErrorGroupDto> getGroups(
            String service, String environment, ErrorSeverity severity,
            ErrorStatus status, String fingerprint, String timeRange,
            OffsetDateTime customStart, OffsetDateTime customEnd,
            String search, Pageable pageable) {

        OffsetDateTime start = customStart;
        OffsetDateTime end = customEnd != null ? customEnd : OffsetDateTime.now();

        if (timeRange != null && !timeRange.equalsIgnoreCase("CUSTOM")) {
            OffsetDateTime now = OffsetDateTime.now();
            start = switch (timeRange.toUpperCase()) {
                case "LAST_1_HOUR" -> now.minus(1, ChronoUnit.HOURS);
                case "LAST_24_HOURS" -> now.minus(24, ChronoUnit.HOURS);
                case "LAST_7_DAYS" -> now.minus(7, ChronoUnit.DAYS);
                case "LAST_30_DAYS" -> now.minus(30, ChronoUnit.DAYS);
                default -> null;
            };
        }

        Page<ErrorGroup> groups = groupRepository.findFiltered(
                service, environment, severity, status, fingerprint, start, end, search, pageable);

        return groups.map(this::toGroupDto);
    }

    @Transactional(readOnly = true)
    public Optional<ErrorGroupDto> getGroupByFingerprint(String fingerprint) {
        return groupRepository.findByFingerprint(fingerprint).map(this::toGroupDto);
    }

    @Transactional(readOnly = true)
    public List<ErrorRecordDto> getRecentOccurrences(String fingerprint) {
        return recordRepository.findTop20ByFingerprintOrderByCreatedAtDesc(fingerprint)
                .stream().map(this::toRecordDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ErrorRecordDto> getRecordById(Long id) {
        return recordRepository.findById(id).map(this::toRecordDto);
    }

    @Transactional
    public Optional<ErrorGroupDto> triageGroup(String fingerprint, TriageRequest triage) {
        return groupRepository.findByFingerprint(fingerprint).map(group -> {
            if (triage.getStatus() != null) {
                group.setStatus(triage.getStatus());
            }
            if (triage.getAssignedTo() != null) {
                group.setAssignedTo(triage.getAssignedTo());
            }
            return toGroupDto(groupRepository.save(group));
        });
    }

    @Transactional
    public boolean deleteGroup(String fingerprint) {
        return groupRepository.findByFingerprint(fingerprint).map(group -> {
            groupRepository.delete(group);
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public ErrorOverviewDto getOverview() {
        Long totalOccurrences = groupRepository.sumTotalOccurrences();
        Long totalGroups = groupRepository.count();
        Long newErrors = groupRepository.countNewErrors();
        Long unresolvedCritical = groupRepository.countUnresolvedCritical();

        OffsetDateTime dayAgo = OffsetDateTime.now().minus(24, ChronoUnit.HOURS);
        Long last24hCount = recordRepository.countSince(dayAgo);

        Map<String, Long> serviceBreakdown = new LinkedHashMap<>();
        for (Object[] row : groupRepository.countByService()) {
            serviceBreakdown.put((String) row[0], (Long) row[1]);
        }

        Map<String, Long> severityBreakdown = new LinkedHashMap<>();
        for (Object[] row : groupRepository.countBySeverity()) {
            severityBreakdown.put(row[0].toString(), (Long) row[1]);
        }

        Map<String, Long> statusBreakdown = new LinkedHashMap<>();
        for (Object[] row : groupRepository.countByStatus()) {
            statusBreakdown.put(row[0].toString(), (Long) row[1]);
        }

        Map<String, Long> hourlyTrend = new LinkedHashMap<>();
        for (Object[] row : recordRepository.getHourlyCountsSince(dayAgo)) {
            hourlyTrend.put(row[0] + ":00", (Long) row[1]);
        }

        List<ErrorGroupDto> recent = groupRepository.findTop10ByOrderByLastSeenDesc()
                .stream().map(this::toGroupDto).toList();

        return ErrorOverviewDto.builder()
                .totalOccurrences(totalOccurrences)
                .totalGroups(totalGroups)
                .newErrorsCount(newErrors)
                .unresolvedCriticalCount(unresolvedCritical)
                .last24HoursCount(last24hCount)
                .serviceBreakdown(serviceBreakdown)
                .severityBreakdown(severityBreakdown)
                .statusBreakdown(statusBreakdown)
                .hourlyTrend24h(hourlyTrend)
                .recentGroups(recent)
                .build();
    }

    public ErrorGroupDto toGroupDto(ErrorGroup g) {
        return ErrorGroupDto.builder()
                .id(g.getId())
                .fingerprint(g.getFingerprint())
                .serviceName(g.getServiceName())
                .environment(g.getEnvironment())
                .errorType(g.getErrorType())
                .message(g.getMessage())
                .severity(g.getSeverity())
                .status(g.getStatus())
                .assignedTo(g.getAssignedTo())
                .occurrencesCount(g.getOccurrencesCount())
                .firstSeen(g.getFirstSeen())
                .lastSeen(g.getLastSeen())
                .build();
    }

    public ErrorRecordDto toRecordDto(ErrorRecord r) {
        Map<String, Object> metadata = null;
        if (r.getMetadataJson() != null) {
            try {
                metadata = objectMapper.readValue(r.getMetadataJson(), new TypeReference<>() {});
            } catch (Exception ignored) {}
        }

        List<BreadcrumbDto> breadcrumbs = null;
        if (r.getBreadcrumbsJson() != null) {
            try {
                breadcrumbs = objectMapper.readValue(r.getBreadcrumbsJson(), new TypeReference<>() {});
            } catch (Exception ignored) {}
        }

        return ErrorRecordDto.builder()
                .id(r.getId())
                .groupId(r.getGroup() != null ? r.getGroup().getId() : null)
                .fingerprint(r.getFingerprint())
                .serviceName(r.getServiceName())
                .version(r.getVersion())
                .environment(r.getEnvironment())
                .releaseTag(r.getReleaseTag())
                .errorType(r.getErrorType())
                .message(r.getMessage())
                .stackTrace(r.getStackTrace())
                .exceptionChain(r.getExceptionChain())
                .requestMethod(r.getRequestMethod())
                .requestUri(r.getRequestUri())
                .responseStatus(r.getResponseStatus())
                .traceId(r.getTraceId())
                .correlationId(r.getCorrelationId())
                .userId(r.getUserId())
                .userRole(r.getUserRole())
                .browser(r.getBrowser())
                .os(r.getOs())
                .frontendRoute(r.getFrontendRoute())
                .severity(r.getSeverity())
                .metadata(metadata)
                .breadcrumbs(breadcrumbs)
                .createdAt(r.getCreatedAt())
                .build();
    }
}
