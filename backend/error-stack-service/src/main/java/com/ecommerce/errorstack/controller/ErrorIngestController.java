package com.ecommerce.errorstack.controller;

import com.ecommerce.errorstack.dto.ErrorIngestRequest;
import com.ecommerce.errorstack.dto.ErrorRecordDto;
import com.ecommerce.errorstack.service.ErrorStackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/errors")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Error Ingestion", description = "Ingestion endpoints for backend exceptions, frontend error boundaries, and unhandled browser rejections")
public class ErrorIngestController {

    private final ErrorStackService errorStackService;

    @Operation(summary = "Ingest Single Application Error", description = "Asynchronously processes, sanitizes, groups, and persists an error event.")
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingestError(
            @Valid @RequestBody ErrorIngestRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole) {

        if (request.getUserId() == null && headerUserId != null) {
            request.setUserId(headerUserId);
        }
        if (request.getUserRole() == null && headerUserRole != null) {
            request.setUserRole(headerUserRole);
        }

        try {
            ErrorRecordDto record = errorStackService.ingest(request);
            if (record != null) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                        "status", "ACCEPTED",
                        "fingerprint", record.getFingerprint(),
                        "recordId", record.getId()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                        "status", "RATE_LIMITED",
                        "message", "Error intake rate limit exceeded"
                ));
            }
        } catch (Exception ex) {
            log.error("Non-fatal failure during error ingestion: {}", ex.getMessage());
            // Return 202 anyway to ensure client application error reporting NEVER breaks the original app
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "status", "DEFERRED",
                    "message", "Ingestion recorded with fallback"
            ));
        }
    }

    @Operation(summary = "Batch Ingest Application Errors", description = "Ingest multiple buffered error events in a single payload.")
    @PostMapping("/ingest/batch")
    public ResponseEntity<Map<String, Object>> ingestBatch(
            @RequestBody List<@Valid ErrorIngestRequest> requests) {

        int ingested = 0;
        List<String> fingerprints = new ArrayList<>();

        if (requests != null) {
            for (ErrorIngestRequest req : requests) {
                try {
                    ErrorRecordDto dto = errorStackService.ingest(req);
                    if (dto != null) {
                        ingested++;
                        fingerprints.add(dto.getFingerprint());
                    }
                } catch (Exception ex) {
                    log.debug("Batch item ingestion failure: {}", ex.getMessage());
                }
            }
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "ACCEPTED",
                "ingestedCount", ingested,
                "fingerprints", fingerprints
        ));
    }
}
