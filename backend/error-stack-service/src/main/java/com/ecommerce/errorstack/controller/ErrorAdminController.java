package com.ecommerce.errorstack.controller;

import com.ecommerce.errorstack.dto.*;
import com.ecommerce.errorstack.model.ErrorSeverity;
import com.ecommerce.errorstack.model.ErrorStatus;
import com.ecommerce.errorstack.service.ErrorStackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/errors/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Error Stack Administration", description = "Admin dashboard APIs for searching, reviewing, triaging, and diagnosing platform errors")
@SecurityRequirement(name = "bearerAuth")
public class ErrorAdminController {

    private final ErrorStackService errorStackService;

    @Operation(summary = "Get Platform Error Overview & Health Metrics", description = "Retrieves total errors, unresolved criticals, service breakdown, severity distribution, and hourly volume.")
    @GetMapping("/overview")
    public ResponseEntity<ErrorOverviewDto> getOverview(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        assertManagerOrAdmin(role);
        return ResponseEntity.ok(errorStackService.getOverview());
    }

    @Operation(summary = "Search & Filter Error Groups", description = "Server-side paginated list of clustered error groups with multi-field filters.")
    @GetMapping("/groups")
    public ResponseEntity<Page<ErrorGroupDto>> getGroups(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) ErrorSeverity severity,
            @RequestParam(required = false) ErrorStatus status,
            @RequestParam(required = false) String fingerprint,
            @RequestParam(defaultValue = "LAST_7_DAYS") String timeRange,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "lastSeen") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        assertManagerOrAdmin(role);
        if (size > 100) size = 100;

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        return ResponseEntity.ok(errorStackService.getGroups(
                service, environment, severity, status, fingerprint,
                timeRange, startDate, endDate, search, pageable));
    }

    @Operation(summary = "Get Group Detail & Occurrences", description = "Retrieves group summary along with up to 20 most recent occurrences.")
    @GetMapping("/groups/{fingerprint}")
    public ResponseEntity<Map<String, Object>> getGroupDetail(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String fingerprint) {

        assertManagerOrAdmin(role);
        ErrorGroupDto group = errorStackService.getGroupByFingerprint(fingerprint)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Error group not found"));

        List<ErrorRecordDto> occurrences = errorStackService.getRecentOccurrences(fingerprint);

        return ResponseEntity.ok(Map.of(
                "group", group,
                "recentOccurrences", occurrences
        ));
    }

    @Operation(summary = "Get Specific Error Occurrence Record", description = "Retrieves full diagnostic record including stack trace, request headers, breadcrumbs, and user context.")
    @GetMapping("/records/{id}")
    public ResponseEntity<ErrorRecordDto> getRecord(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id) {

        assertManagerOrAdmin(role);
        ErrorRecordDto record = errorStackService.getRecordById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Error record not found"));

        return ResponseEntity.ok(record);
    }

    @Operation(summary = "Triage Error Group", description = "Updates error status (NEW, INVESTIGATING, RESOLVED, IGNORED) and assigns to staff member.")
    @PatchMapping("/groups/{fingerprint}/triage")
    public ResponseEntity<ErrorGroupDto> triageGroup(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String fingerprint,
            @RequestBody TriageRequest triage) {

        assertManagerOrAdmin(role);
        ErrorGroupDto updated = errorStackService.triageGroup(fingerprint, triage)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Error group not found"));

        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Delete / Dismiss Error Group", description = "Permanently removes an error group and all linked occurrences.")
    @DeleteMapping("/groups/{fingerprint}")
    public ResponseEntity<Map<String, String>> deleteGroup(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String fingerprint) {

        assertManagerOrAdmin(role);
        boolean deleted = errorStackService.deleteGroup(fingerprint);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Error group not found");
        }
        return ResponseEntity.ok(Map.of("message", "Error group successfully deleted"));
    }

    private void assertManagerOrAdmin(String role) {
        if (role == null || (!role.toUpperCase().contains("ADMIN") && !role.toUpperCase().contains("MANAGER"))) {
            log.warn("Security Alert: Unauthorized access attempt to Error Stack Admin by role: {}", role);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Staff authorization (Manager or Admin) required.");
        }
    }
}
