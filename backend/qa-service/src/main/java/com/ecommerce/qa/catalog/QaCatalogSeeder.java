package com.ecommerce.qa.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class QaCatalogSeeder {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public QaCatalogSeeder(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        QaCatalogRegistry.FEATURES.forEach(feature -> {
            jdbc.update("""
                INSERT INTO qa_features(feature_id, feature_name, domain_name, frontend_module, backend_service,
                    api_endpoints, database_dependencies, permissions, coverage_status, coverage_note, updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,NOW())
                ON CONFLICT(feature_id) DO UPDATE SET feature_name=EXCLUDED.feature_name, domain_name=EXCLUDED.domain_name,
                    frontend_module=EXCLUDED.frontend_module, backend_service=EXCLUDED.backend_service,
                    api_endpoints=EXCLUDED.api_endpoints, database_dependencies=EXCLUDED.database_dependencies,
                    permissions=EXCLUDED.permissions, coverage_status=EXCLUDED.coverage_status,
                    coverage_note=EXCLUDED.coverage_note, updated_at=NOW()
                """, feature.id(), feature.name(), feature.domain(), feature.frontendModule(), feature.backendService(),
                    json(feature.endpoints()), json(feature.databases()), json(feature.permissions()),
                    feature.coverageStatus(), feature.coverageNote());
            jdbc.update("DELETE FROM qa_feature_tests WHERE feature_id=?", feature.id());
            feature.tests().forEach(test -> jdbc.update("""
                INSERT INTO qa_feature_tests(feature_id, test_id, test_name, test_class, test_layer, case_count, permission_tested)
                VALUES(?,?,?,?,?,?,?)
                """, feature.id(), test.testId(), test.name(), test.testClass(), test.layer(), test.caseCount(), emptyToNull(test.permission())));
        });

        QaCatalogRegistry.SUITES.forEach(suite -> jdbc.update("""
            INSERT INTO qa_test_suites(suite_id, suite_name, description, executable, runner_key, updated_at)
            VALUES(?,?,?,?,?,NOW())
            ON CONFLICT(suite_id) DO UPDATE SET suite_name=EXCLUDED.suite_name, description=EXCLUDED.description,
                executable=EXCLUDED.executable, runner_key=EXCLUDED.runner_key, updated_at=NOW()
            """, suite.id(), suite.name(), suite.description(), suite.executable(), suite.runnerKey()));

        QaCatalogRegistry.DEFAULT_ROLES.forEach(role -> {
            jdbc.update("""
                INSERT INTO qa_rbac_roles(role_key, display_name, source_note) VALUES(?,?,?)
                ON CONFLICT(role_key) DO UPDATE SET display_name=EXCLUDED.display_name, source_note=EXCLUDED.source_note
                """, role.key(), role.name(), "Baseline sourced from user-service V4 hierarchical RBAC migration; custom roles require a live user-service sync.");
            jdbc.update("DELETE FROM qa_rbac_role_permissions WHERE role_key=?", role.key());
            role.permissions().forEach(permission -> jdbc.update(
                    "INSERT INTO qa_rbac_role_permissions(role_key, permission_key) VALUES(?,?)",
                    role.key(), permission));
        });
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to serialize the version-controlled QA catalog", ex); }
    }

    private static String emptyToNull(String value) { return value == null || value.isBlank() ? null : value; }
}
