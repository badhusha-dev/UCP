package com.ecommerce.qa.api;

import com.ecommerce.qa.persistence.QaRepository;
import com.ecommerce.qa.security.QaPermissionService;
import com.ecommerce.qa.service.QaCatalogService;
import com.ecommerce.qa.service.QaRunService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/qa")
public class QaController {
    private final QaPermissionService permissions;
    private final QaCatalogService catalog;
    private final QaRunService runs;
    private final QaRepository repository;

    public QaController(QaPermissionService permissions, QaCatalogService catalog, QaRunService runs, QaRepository repository) {
        this.permissions = permissions;
        this.catalog = catalog;
        this.runs = runs;
        this.repository = repository;
    }

    @GetMapping("/overview")
    public QaModels.Overview overview(HttpServletRequest request) {
        authorize(request, "qa:read");
        return catalog.overview();
    }

    @GetMapping("/features")
    public QaModels.Page<QaModels.Feature> features(HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String search, @RequestParam(required = false) String service,
            @RequestParam(required = false) String coverage) {
        authorize(request, "qa:read");
        return catalog.features(page, size, search, service, coverage);
    }

    @GetMapping("/features/{featureId}")
    public QaModels.Feature feature(HttpServletRequest request, @PathVariable String featureId) {
        authorize(request, "qa:read");
        return catalog.feature(featureId);
    }

    @GetMapping("/tests")
    public QaModels.Page<QaModels.TestCase> tests(HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String service, @RequestParam(required = false) String layer,
            @RequestParam(required = false) String featureId) {
        authorize(request, "qa:read");
        return catalog.tests(page, size, service, layer, featureId);
    }

    @GetMapping("/suites")
    public List<QaModels.Suite> suites(HttpServletRequest request) {
        authorize(request, "qa:read");
        return catalog.suites();
    }

    @GetMapping("/rbac/matrix")
    public List<QaModels.RbacCell> rbacMatrix(HttpServletRequest request) {
        authorize(request, "qa:read");
        return catalog.rbacMatrix();
    }

    @GetMapping("/coverage")
    public QaModels.Coverage coverage(HttpServletRequest request) {
        authorize(request, "qa:read");
        return catalog.coverage();
    }

    @PostMapping("/runs")
    public ResponseEntity<QaModels.Run> start(HttpServletRequest request, @Valid @RequestBody CreateRunRequest body) {
        QaPermissionService.Actor actor = authorize(request, "qa:run");
        return ResponseEntity.accepted().body(runs.start(body, actor));
    }

    @GetMapping("/runs")
    public QaModels.Page<QaModels.Run> runs(HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status, @RequestParam(required = false) String suiteId,
            @RequestParam(required = false) String featureId) {
        authorize(request, "qa:read");
        if (page < 0 || page > 10_000 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination parameters.");
        return repository.runs(page, size, status, suiteId, featureId);
    }

    @GetMapping("/runs/{runId}")
    public QaModels.Run run(HttpServletRequest request, @PathVariable UUID runId) {
        authorize(request, "qa:read");
        return findRun(runId);
    }

    @GetMapping("/runs/{runId}/results")
    public QaModels.Page<QaModels.Result> results(HttpServletRequest request, @PathVariable UUID runId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String status) {
        authorize(request, "qa:read");
        findRun(runId);
        if (page < 0 || page > 10_000 || size < 1 || size > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination parameters.");
        long total = repository.resultCount(runId, status);
        return new QaModels.Page<>(repository.results(runId, page, size, status), page, size, total,
                total == 0 ? 0 : (int) Math.ceil(total / (double) size));
    }

    @GetMapping("/runs/{runId}/logs")
    public QaModels.RunLog logs(HttpServletRequest request, @PathVariable UUID runId) {
        authorize(request, "qa:read");
        findRun(runId);
        return new QaModels.RunLog(runId, repository.executionLog(runId));
    }

    @GetMapping("/runs/{runId}/artifacts")
    public List<QaModels.Artifact> artifacts(HttpServletRequest request, @PathVariable UUID runId) {
        authorize(request, "qa:read");
        findRun(runId);
        boolean hasResults = repository.resultCount(runId, null) > 0;
        boolean hasLog = repository.executionLog(runId) != null && !repository.executionLog(runId).isBlank();
        return List.of(new QaModels.Artifact("REPORT", "JUnit XML report", hasResults, "junit"),
                new QaModels.Artifact("LOG", "Sanitized execution log", hasLog, "text"),
                new QaModels.Artifact("REPORT", "JSON run report", true, "json"));
    }

    @GetMapping("/runs/{runId}/report")
    public ResponseEntity<?> report(HttpServletRequest request, @PathVariable UUID runId,
            @RequestParam(defaultValue = "json") String format) {
        authorize(request, "qa:read");
        QaModels.Run run = findRun(runId);
        List<QaModels.Result> results = repository.allResults(runId);
        if ("json".equalsIgnoreCase(format)) {
            List<QaModels.Result> rbac = results.stream().filter(result -> result.permission() != null || "rbac".equals(result.featureId())).toList();
            return ResponseEntity.ok(new QaModels.Report(run, results, rbac));
        }
        if ("junit".equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=qa-run-" + runId + ".xml")
                    .body(junitXml(run, results));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supported report formats are json and junit.");
    }

    @PostMapping("/runs/{runId}/cancel")
    public QaModels.Run cancel(HttpServletRequest request, @PathVariable UUID runId) {
        return runs.cancel(runId, authorize(request, "qa:cancel"));
    }

    @PostMapping("/runs/{runId}/rerun-failures")
    public ResponseEntity<QaModels.Run> rerunFailures(HttpServletRequest request, @PathVariable UUID runId) {
        return ResponseEntity.accepted().body(runs.rerunFailures(runId, authorize(request, "qa:run")));
    }

    private QaPermissionService.Actor authorize(HttpServletRequest request, String permission) {
        return permissions.require(permission, request.getHeader("X-User-Id"), request.getHeader("X-User-Email"),
                request.getHeader("X-User-Roles"), request.getHeader("X-User-Role"), request.getHeader("X-User-Permissions"));
    }

    private QaModels.Run findRun(UUID runId) {
        return repository.run(runId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test run not found."));
    }

    private static String junitXml(QaModels.Run run, List<QaModels.Result> results) {
        long failures = results.stream().filter(result -> "FAILED".equals(result.status())).count();
        long errors = results.stream().filter(result -> List.of("BLOCKED", "INFRASTRUCTURE_FAILURE", "TIMED_OUT").contains(result.status())).count();
        long skipped = results.stream().filter(result -> "SKIPPED".equals(result.status())).count();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuite name=\"UCP QA run ").append(run.id()).append("\" tests=\"").append(results.size())
                .append("\" failures=\"").append(failures).append("\" errors=\"").append(errors)
                .append("\" skipped=\"").append(skipped).append("\">\n");
        for (QaModels.Result result : results) {
            xml.append("  <testcase classname=\"").append(escape(result.serviceName())).append("\" name=\"")
                    .append(escape(result.testName())).append("\" time=\"").append(result.durationMs() / 1000.0).append("\">");
            if ("FAILED".equals(result.status())) xml.append("<failure message=\"").append(escape(result.errorMessage())).append("\">").append(escape(result.actual())).append("</failure>");
            else if ("SKIPPED".equals(result.status())) xml.append("<skipped/>");
            else if (List.of("BLOCKED", "INFRASTRUCTURE_FAILURE", "TIMED_OUT").contains(result.status()))
                xml.append("<error message=\"").append(escape(result.errorMessage())).append("\">").append(escape(result.actual())).append("</error>");
            xml.append("</testcase>\n");
        }
        return xml.append("</testsuite>\n").toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
