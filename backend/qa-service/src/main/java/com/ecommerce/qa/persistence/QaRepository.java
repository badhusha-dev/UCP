package com.ecommerce.qa.persistence;

import com.ecommerce.qa.api.QaModels;
import com.ecommerce.qa.catalog.QaCatalogRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class QaRepository {
    private static final String FEATURE_SELECT = """
        SELECT f.feature_id, f.feature_name, f.domain_name, f.frontend_module, f.backend_service,
               f.api_endpoints, f.database_dependencies, f.permissions, f.coverage_status, f.coverage_note,
               COALESCE((SELECT r.status FROM qa_test_runs r LEFT JOIN qa_test_results tr ON tr.run_id=r.run_id
                         WHERE r.feature_id=f.feature_id OR tr.feature_id=f.feature_id ORDER BY r.created_at DESC LIMIT 1), 'NOT_RUN') AS last_run_status,
               (SELECT r.created_at FROM qa_test_runs r LEFT JOIN qa_test_results tr ON tr.run_id=r.run_id
                WHERE r.feature_id=f.feature_id OR tr.feature_id=f.feature_id ORDER BY r.created_at DESC LIMIT 1) AS last_run_at,
               (SELECT COALESCE(SUM(case_count),0) FROM qa_feature_tests t WHERE t.feature_id=f.feature_id) AS registered_count
          FROM qa_features f
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public QaRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public QaModels.Page<QaModels.Feature> features(int page, int size, String search, String service, String coverage) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (hasText(search)) {
            where.append(" AND (LOWER(f.feature_name) LIKE ? OR LOWER(f.feature_id) LIKE ? OR LOWER(f.domain_name) LIKE ?)");
            String pattern = "%" + search.toLowerCase() + "%";
            args.add(pattern); args.add(pattern); args.add(pattern);
        }
        if (hasText(service)) {
            where.append(" AND LOWER(f.backend_service) LIKE ?");
            args.add("%" + service.toLowerCase() + "%");
        }
        if (hasText(coverage)) {
            where.append(" AND f.coverage_status=?");
            args.add(coverage.toUpperCase());
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM qa_features f" + where, Long.class, args.toArray());
        args.add(size); args.add(page * size);
        List<QaModels.Feature> content = jdbc.query(FEATURE_SELECT + where + " ORDER BY f.domain_name, f.feature_name LIMIT ? OFFSET ?",
                featureRowMapper(), args.toArray());
        return page(content, page, size, count == null ? 0 : count);
    }

    public Optional<QaModels.Feature> feature(String featureId) {
        List<QaModels.Feature> found = jdbc.query(FEATURE_SELECT + " WHERE f.feature_id=?", featureRowMapper(), featureId);
        return found.stream().findFirst();
    }

    public List<QaModels.Feature> allFeatures() {
        return jdbc.query(FEATURE_SELECT + " ORDER BY f.domain_name, f.feature_name", featureRowMapper());
    }

    public QaModels.Page<QaModels.TestCase> tests(int page, int size, String service, String layer, String featureId) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (hasText(service)) { where.append(" AND LOWER(f.backend_service) LIKE ?"); args.add("%" + service.toLowerCase() + "%"); }
        if (hasText(layer)) { where.append(" AND t.test_layer=?"); args.add(layer.toUpperCase()); }
        if (hasText(featureId)) { where.append(" AND f.feature_id=?"); args.add(featureId); }
        String from = " FROM qa_feature_tests t JOIN qa_features f ON f.feature_id=t.feature_id";
        Long count = jdbc.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        args.add(size); args.add(page * size);
        List<QaModels.TestCase> rows = jdbc.query("""
            SELECT t.test_id, t.test_name, f.feature_id, f.feature_name, f.backend_service, t.test_layer,
                   t.permission_tested, t.case_count, f.coverage_status
              FROM qa_feature_tests t JOIN qa_features f ON f.feature_id=t.feature_id
            """ + where + " ORDER BY f.domain_name, f.feature_name, t.test_name LIMIT ? OFFSET ?", (rs, row) ->
                new QaModels.TestCase(rs.getString("test_id"), rs.getString("test_name"), rs.getString("feature_id"),
                        rs.getString("feature_name"), rs.getString("backend_service"), rs.getString("test_layer"),
                        rs.getString("permission_tested"), rs.getInt("case_count"), rs.getString("coverage_status")), args.toArray());
        return page(rows, page, size, count == null ? 0 : count);
    }

    public List<QaModels.Suite> suites() {
        return jdbc.query("SELECT suite_id, suite_name, description, executable FROM qa_test_suites ORDER BY executable DESC, suite_name",
                (rs, row) -> new QaModels.Suite(rs.getString("suite_id"), rs.getString("suite_name"), rs.getString("description"), rs.getBoolean("executable")));
    }

    public List<QaModels.RbacRole> roles() {
        return jdbc.query("SELECT role_key, display_name FROM qa_rbac_roles ORDER BY role_key", (rs, row) -> {
            String role = rs.getString("role_key");
            List<String> permissions = jdbc.queryForList("SELECT permission_key FROM qa_rbac_role_permissions WHERE role_key=? ORDER BY permission_key", String.class, role);
            return new QaModels.RbacRole(role, rs.getString("display_name"), permissions);
        });
    }

    public String permissionTestCoverage(String role, String permission) {
        if (role == null || role.isBlank() || permission == null || permission.isBlank()) return "NOT_MAPPED";
        List<String> statuses = jdbc.queryForList("""
            SELECT status FROM qa_test_results WHERE role_name=? AND role_permission=?
            ORDER BY executed_at DESC LIMIT 1
            """, String.class, role, permission);
        return statuses.isEmpty() ? "NOT_RUN" : "ASSERTION_" + statuses.get(0);
    }

    public QaModels.Page<QaModels.Run> runs(int page, int size, String status, String suiteId, String featureId) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (hasText(status)) { where.append(" AND status=?"); args.add(status.toUpperCase()); }
        if (hasText(suiteId)) { where.append(" AND suite_id=?"); args.add(suiteId); }
        if (hasText(featureId)) { where.append(" AND feature_id=?"); args.add(featureId); }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM qa_test_runs" + where, Long.class, args.toArray());
        args.add(size); args.add(page * size);
        List<QaModels.Run> content = jdbc.query("SELECT * FROM qa_test_runs" + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?", runRowMapper(), args.toArray());
        return page(content, page, size, count == null ? 0 : count);
    }

    public Optional<QaModels.Run> run(UUID runId) {
        List<QaModels.Run> rows = jdbc.query("SELECT * FROM qa_test_runs WHERE run_id=?", runRowMapper(), runId);
        return rows.stream().findFirst();
    }

    public List<QaModels.Result> results(UUID runId, int page, int size, String status) {
        String filter = hasText(status) ? " AND status=?" : "";
        String sql = "SELECT * FROM qa_test_results WHERE run_id=?" + filter + " ORDER BY result_id LIMIT ? OFFSET ?";
        if (hasText(status)) return jdbc.query(sql, resultRowMapper(), runId, status.toUpperCase(), size, page * size);
        return jdbc.query(sql, resultRowMapper(), runId, size, page * size);
    }

    public long resultCount(UUID runId, String status) {
        if (hasText(status)) return value(jdbc.queryForObject("SELECT COUNT(*) FROM qa_test_results WHERE run_id=? AND status=?", Long.class, runId, status.toUpperCase()));
        return value(jdbc.queryForObject("SELECT COUNT(*) FROM qa_test_results WHERE run_id=?", Long.class, runId));
    }

    public List<QaModels.Result> allResults(UUID runId) {
        return jdbc.query("SELECT * FROM qa_test_results WHERE run_id=? ORDER BY result_id", resultRowMapper(), runId);
    }

    public String executionLog(UUID runId) {
        return jdbc.query("SELECT execution_log FROM qa_test_runs WHERE run_id=?", rs -> rs.next() ? rs.getString(1) : null, runId);
    }

    public QaModels.Overview overview() {
        long totalFeatures = value(jdbc.queryForObject("SELECT COUNT(*) FROM qa_features", Long.class));
        long testedFeatures = value(jdbc.queryForObject("SELECT COUNT(DISTINCT feature_id) FROM qa_feature_tests", Long.class));
        long testCases = value(jdbc.queryForObject("SELECT COALESCE(SUM(case_count),0) FROM qa_feature_tests", Long.class));
        long endpoints = allFeatures().stream().mapToLong(f -> f.endpoints().size()).sum();
        Optional<QaModels.Run> latest = jdbc.query("SELECT * FROM qa_test_runs ORDER BY created_at DESC LIMIT 1", runRowMapper()).stream().findFirst();
        QaModels.Run recent = latest.orElse(null);
        long rbacCases = value(jdbc.queryForObject("SELECT COALESCE(SUM(case_count),0) FROM qa_feature_tests WHERE feature_id='rbac'", Long.class));
        long lastRbac = value(jdbc.queryForObject("SELECT COUNT(*) FROM qa_test_results r JOIN qa_test_runs q ON q.run_id=r.run_id WHERE q.suite_id='rbac' AND q.run_id=(SELECT run_id FROM qa_test_runs WHERE suite_id='rbac' ORDER BY created_at DESC LIMIT 1)", Long.class));
        String lastSuccessful = jdbc.query("SELECT finished_at FROM qa_test_runs WHERE suite_id IN ('all','regression') AND status='PASSED' ORDER BY finished_at DESC LIMIT 1",
                rs -> rs.next() ? instant(rs.getTimestamp(1)).toString() : null);
        List<QaModels.Run> recentRuns = jdbc.query("SELECT * FROM qa_test_runs ORDER BY created_at DESC LIMIT 8", runRowMapper());
        return new QaModels.Overview(totalFeatures, testedFeatures, totalFeatures - testedFeatures, testCases, endpoints,
                "NOT_MEASURED", recent == null ? "NOT_RUN" : recent.status(), recent == null ? 0 : recent.passedTests(),
                recent == null ? 0 : recent.failedTests(), recent == null ? 0 : recent.skippedTests(),
                recent == null ? 0 : recent.blockedTests(), recent == null ? 0 : recent.infrastructureFailures(),
                lastRbac == 0 ? "NOT_RUN" : "RESULTS_AVAILABLE", rbacCases, lastRbac, lastSuccessful, recentRuns);
    }

    public List<QaModels.CoverageFeature> coverage() {
        return allFeatures().stream().map(feature -> {
            long rbacTests = jdbc.queryForObject("SELECT COALESCE(SUM(case_count),0) FROM qa_feature_tests WHERE feature_id=? AND permission_tested IS NOT NULL", Long.class, feature.id());
            int endpointCount = feature.endpoints().size();
            return new QaModels.CoverageFeature(feature.id(), feature.name(), feature.backendService(),
                    feature.automatedTestCount(), feature.coverageStatus(), endpointCount, "NOT_MEASURED",
                    Math.toIntExact(rbacTests), rbacTests == 0 ? "NOT_MAPPED" : "PARTIAL");
        }).toList();
    }

    public void createRun(QaModels.Run run) {
        jdbc.update("""
            INSERT INTO qa_test_runs(run_id,suite_id,feature_id,environment,source_version,requested_by,requested_by_email,
                status,phase,total_tests,retry_of)
            VALUES(?,?,?,?,?,?,?,'QUEUED','QUEUED',?,?)
            """, run.id(), run.suiteId(), run.featureId(), run.environment(), run.sourceVersion(), run.requestedBy(),
                run.requestedByEmail(), run.totalTests(), run.retryOf());
    }

    public boolean acquireRunLock(UUID runId) {
        return jdbc.update("INSERT INTO qa_active_run_lock(lock_id,run_id) VALUES(1,?) ON CONFLICT(lock_id) DO NOTHING", runId) == 1;
    }

    public void audit(long actorId, String email, String action, UUID runId, String details) {
        jdbc.update("INSERT INTO qa_audit_log(actor_user_id,actor_email,action,run_id,details) VALUES(?,?,?,?,?)",
                actorId, email, action, runId, details == null ? "" : details);
    }

    public long countRecentRuns(long actorId, Instant since) {
        return value(jdbc.queryForObject("SELECT COUNT(*) FROM qa_test_runs WHERE requested_by=? AND created_at>=?", Long.class, actorId, Timestamp.from(since)));
    }

    public void setStatus(UUID runId, String status, String phase, String errorType, String errorMessage) {
        jdbc.update("UPDATE qa_test_runs SET status=?,phase=?,error_type=?,error_message=? WHERE run_id=? AND status NOT IN ('PASSED','FAILED','BLOCKED','CANCELLED')",
                status, phase, errorType, errorMessage, runId);
    }

    public void start(UUID runId) {
        jdbc.update("UPDATE qa_test_runs SET status='PREFLIGHT',phase='PREFLIGHT',started_at=COALESCE(started_at,NOW()) WHERE run_id=? AND status='QUEUED'", runId);
    }

    public void markRunning(UUID runId, String phase, String current, int total) {
        jdbc.update("UPDATE qa_test_runs SET status='RUNNING',phase=?,current_test=?,total_tests=?,started_at=COALESCE(started_at,NOW()) WHERE run_id=? AND status IN ('PREFLIGHT','RUNNING')",
                phase, current, total, runId);
    }

    public void updateProgress(UUID runId, String current, int completed, int passed, int failed, int skipped) {
        jdbc.update("""
            UPDATE qa_test_runs SET current_test=?,completed_tests=?,passed_tests=?,failed_tests=?,skipped_tests=?
            WHERE run_id=? AND status='RUNNING'
            """, current, completed, passed, failed, skipped, runId);
    }

    public void addExecutionLog(UUID runId, String safeLog) {
        jdbc.update("UPDATE qa_test_runs SET execution_log=? WHERE run_id=?", safeLog, runId);
    }

    public void addResult(QaModels.Result result) {
        jdbc.update("""
            INSERT INTO qa_test_results(run_id,test_id,test_name,feature_id,service_name,test_layer,role_name,role_permission,status,
                duration_ms,expected_result,actual_result,error_message,log_excerpt,evidence_reference,executed_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,COALESCE(?,NOW()))
            """, result.runId(), result.testId(), result.testName(), result.featureId(), result.serviceName(), result.layer(),
                result.roleName(), result.permission(), result.status(), result.durationMs(), result.expected(), result.actual(),
                result.errorMessage(), result.logExcerpt(), result.evidenceReference(), result.executedAt() == null ? null : Timestamp.from(result.executedAt()));
    }

    public void replaceResults(UUID runId, List<QaModels.Result> results) {
        jdbc.update("DELETE FROM qa_test_results WHERE run_id=?", runId);
        results.forEach(this::addResult);
    }

    public void finish(UUID runId, String status, String phase, int total, int completed, int passed, int failed,
                       int skipped, int blocked, int infrastructureFailures, String errorType, String errorMessage, String log) {
        jdbc.update("""
            UPDATE qa_test_runs SET status=?,phase=?,current_test=NULL,total_tests=?,completed_tests=?,passed_tests=?,
                failed_tests=?,skipped_tests=?,blocked_tests=?,infrastructure_failures=?,error_type=?,error_message=?,
                execution_log=?,finished_at=NOW(),duration_ms=COALESCE(EXTRACT(EPOCH FROM (NOW()-started_at))*1000,0)::BIGINT
            WHERE run_id=? AND status<>'CANCELLED'
            """, status, phase, total, completed, passed, failed, skipped, blocked, infrastructureFailures,
                errorType, errorMessage, log == null ? "" : log, runId);
        if (terminal(status)) jdbc.update("DELETE FROM qa_active_run_lock WHERE run_id=?", runId);
    }

    public boolean cancel(UUID runId) {
        int updated = jdbc.update("""
            UPDATE qa_test_runs SET status='CANCELLED',phase='CANCELLED',current_test=NULL,finished_at=NOW(),
                duration_ms=COALESCE(EXTRACT(EPOCH FROM (NOW()-started_at))*1000,0)::BIGINT
            WHERE run_id=? AND status IN ('QUEUED','PREFLIGHT','RUNNING')
            """, runId);
        if (updated > 0) jdbc.update("DELETE FROM qa_active_run_lock WHERE run_id=?", runId);
        return updated > 0;
    }

    public void recoverInterruptedRuns() {
        jdbc.update("""
            UPDATE qa_test_runs SET status='BLOCKED',phase='BLOCKED',error_type='RUNNER_RESTARTED',
                error_message='The QA service restarted before this run completed.',finished_at=NOW()
            WHERE status IN ('QUEUED','PREFLIGHT','RUNNING','FINALIZING')
            """);
        jdbc.update("DELETE FROM qa_active_run_lock");
    }

    public List<QaModels.Result> failedResults(UUID runId) {
        return jdbc.query("SELECT * FROM qa_test_results WHERE run_id=? AND status IN ('FAILED','INFRASTRUCTURE_FAILURE','TIMED_OUT') ORDER BY result_id",
                resultRowMapper(), runId);
    }

    private RowMapper<QaModels.Feature> featureRowMapper() {
        return (rs, row) -> {
            String id = rs.getString("feature_id");
            return new QaModels.Feature(id, rs.getString("feature_name"), rs.getString("domain_name"),
                    rs.getString("frontend_module"), rs.getString("backend_service"), jsonList(rs.getString("api_endpoints")),
                    jsonList(rs.getString("database_dependencies")), jsonList(rs.getString("permissions")),
                    rs.getString("coverage_status"), rs.getString("coverage_note"), rs.getInt("registered_count"),
                    rs.getString("last_run_status"), instant(rs.getTimestamp("last_run_at")), testsFor(id));
        };
    }

    private List<QaModels.TestMapping> testsFor(String featureId) {
        return jdbc.query("SELECT * FROM qa_feature_tests WHERE feature_id=? ORDER BY test_name", (rs, row) ->
                new QaModels.TestMapping(rs.getString("test_id"), rs.getString("test_name"), rs.getString("test_class"),
                        rs.getString("test_layer"), rs.getInt("case_count"), rs.getString("permission_tested")), featureId);
    }

    private RowMapper<QaModels.Run> runRowMapper() {
        return (rs, row) -> new QaModels.Run(rs.getObject("run_id", UUID.class), rs.getString("suite_id"), rs.getString("feature_id"),
                rs.getString("environment"), rs.getString("source_version"), rs.getLong("requested_by"), rs.getString("requested_by_email"),
                rs.getString("status"), rs.getString("phase"), rs.getString("current_test"), rs.getInt("total_tests"),
                rs.getInt("completed_tests"), rs.getInt("passed_tests"), rs.getInt("failed_tests"), rs.getInt("skipped_tests"),
                rs.getInt("blocked_tests"), rs.getInt("infrastructure_failures"), rs.getString("error_type"), rs.getString("error_message"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("finished_at")),
                (Long) rs.getObject("duration_ms"), rs.getObject("retry_of", UUID.class));
    }

    private RowMapper<QaModels.Result> resultRowMapper() {
        return (rs, row) -> new QaModels.Result(rs.getLong("result_id"), rs.getObject("run_id", UUID.class), rs.getString("test_id"),
                rs.getString("test_name"), rs.getString("feature_id"), rs.getString("service_name"), rs.getString("test_layer"),
                rs.getString("role_name"), rs.getString("role_permission"), rs.getString("status"), rs.getLong("duration_ms"),
                rs.getString("expected_result"), rs.getString("actual_result"), rs.getString("error_message"), rs.getString("log_excerpt"),
                rs.getString("evidence_reference"), instant(rs.getTimestamp("executed_at")));
    }

    private List<String> jsonList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try { return objectMapper.readValue(raw, new TypeReference<>() {}); }
        catch (Exception ex) { return List.of(); }
    }

    private static <T> QaModels.Page<T> page(List<T> content, int page, int size, long total) {
        int pages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new QaModels.Page<>(content, page, size, total, pages);
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static long value(Long value) { return value == null ? 0 : value; }
    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
    private static boolean terminal(String status) { return List.of("PASSED", "FAILED", "BLOCKED", "CANCELLED").contains(status); }
}
