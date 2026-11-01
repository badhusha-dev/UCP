package com.ecommerce.qa.runner;

import com.ecommerce.qa.catalog.QaCatalogRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class QaSuiteRegistry {
    private static final Set<String> FRONTEND_CLASSES = Set.of("authSlice.test.ts", "cartSlice.test.ts");

    public QaExecutionPlan resolve(String suiteId, String featureId) {
        if (featureId != null && !featureId.isBlank()) return feature(featureId);
        if (suiteId == null || suiteId.isBlank()) throw badRequest("Select an approved suite or a catalog feature.");
        return switch (suiteId.toLowerCase()) {
            case "all", "regression" -> all(suiteId.toLowerCase());
            case "backend" -> backend();
            case "frontend" -> frontend();
            case "rbac" -> rbac();
            case "integration" -> integration();
            case "smoke", "e2e" -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This suite has no trusted test plan registered yet.");
            default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown QA suite.");
        };
    }

    public QaExecutionPlan retryFailures(Collection<String> failedTestIds) {
        Set<String> classNames = failedTestIds.stream().map(QaSuiteRegistry::classPart).filter(this::isRegisteredClass)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (classNames.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This run has no failed test cases that can be safely mapped back to a registered suite.");
        return fromClasses(classNames, "Retry failed tests");
    }

    public String moduleFor(String testClass) {
        return switch (testClass) {
            case "AuthAndUserIntegrationTest", "UserRepositoryTest", "LoyaltyAndAddressIntegrationTest" -> "user-service";
            case "GatewaySecurityAndRoutingTest", "QualityCenterRbacTest" -> "api-gateway";
            case "QaPermissionServiceTest", "QaCatalogRegistryTest", "QaRunSelectionTest" -> "qa-service";
            case "ConfigServerApplicationTest" -> "config-server";
            case "DiscoveryServerApplicationTest" -> "discovery-server";
            default -> QaCatalogRegistry.FEATURES.stream()
                    .filter(feature -> feature.tests().stream().anyMatch(test -> test.testClass().equals(testClass)))
                    .map(feature -> feature.backendService().split(",")[0].trim())
                    .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "The selected test is not in the trusted registry."));
        };
    }

    public boolean isFrontendClass(String testClass) { return FRONTEND_CLASSES.contains(testClass); }

    private QaExecutionPlan all(String scope) {
        List<QaExecutionPlan.Step> steps = new ArrayList<>();
        steps.add(new QaExecutionPlan.Step("backend", "Backend test suite", "", List.of(), false));
        steps.add(new QaExecutionPlan.Step("frontend", "Shared frontend tests", "", List.of("authSlice.test.ts", "cartSlice.test.ts"), true));
        return new QaExecutionPlan(steps, countTests(t -> !"FRONTEND_UNIT".equals(t.layer())), "all".equals(scope) ? "Run All Tests" : "Regression Tests");
    }

    private QaExecutionPlan backend() {
        return new QaExecutionPlan(List.of(new QaExecutionPlan.Step("backend", "Backend test suite", "", List.of(), false)),
                countTests(t -> !"FRONTEND_UNIT".equals(t.layer())), "Backend Tests");
    }

    private QaExecutionPlan frontend() {
        return new QaExecutionPlan(List.of(new QaExecutionPlan.Step("frontend", "Shared frontend tests", "", List.of("authSlice.test.ts", "cartSlice.test.ts"), true)),
                countTests(t -> "FRONTEND_UNIT".equals(t.layer())), "Frontend Tests");
    }

    private QaExecutionPlan rbac() {
        List<QaExecutionPlan.Step> steps = List.of(
                new QaExecutionPlan.Step("api-gateway", "Gateway QA authorization tests", "api-gateway", List.of("QualityCenterRbacTest"), false),
                new QaExecutionPlan.Step("qa-service", "QA service permission policy tests", "qa-service", List.of("QaPermissionServiceTest"), false));
        return new QaExecutionPlan(steps, 11, "RBAC Tests");
    }

    private QaExecutionPlan integration() {
        LinkedHashSet<String> classes = QaCatalogRegistry.FEATURES.stream().flatMap(feature -> feature.tests().stream())
                .filter(test -> "INTEGRATION".equals(test.layer())).map(QaCatalogRegistry.TestMapping::testClass)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return fromClasses(classes, "Integration Tests");
    }

    private QaExecutionPlan feature(String featureId) {
        QaCatalogRegistry.Feature feature = QaCatalogRegistry.featureById().get(featureId);
        if (feature == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature not found in the trusted catalog.");
        if (feature.tests().isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "This feature has no registered automated tests, so it cannot be reported as passing.");
        Set<String> classNames = feature.tests().stream().map(QaCatalogRegistry.TestMapping::testClass)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return fromClasses(classNames, feature.name());
    }

    private QaExecutionPlan fromClasses(Collection<String> classes, String label) {
        Map<String, List<String>> byModule = new LinkedHashMap<>();
        List<String> frontend = new ArrayList<>();
        int expected = 0;
        Set<String> unique = new LinkedHashSet<>(classes);
        for (String testClass : unique) {
            if (isFrontendClass(testClass)) {
                frontend.add(testClass);
            } else {
                byModule.computeIfAbsent(moduleFor(testClass), ignored -> new ArrayList<>()).add(testClass);
            }
            expected += QaCatalogRegistry.FEATURES.stream().flatMap(feature -> feature.tests().stream())
                    .filter(test -> test.testClass().equals(testClass)).mapToInt(QaCatalogRegistry.TestMapping::caseCount).findFirst().orElse(0);
        }
        List<QaExecutionPlan.Step> steps = new ArrayList<>();
        byModule.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> steps.add(
                new QaExecutionPlan.Step(entry.getKey(), entry.getKey() + " tests", entry.getKey(), entry.getValue(), false)));
        if (!frontend.isEmpty()) steps.add(new QaExecutionPlan.Step("frontend", "Shared frontend tests", "", frontend, true));
        return new QaExecutionPlan(steps, expected, label);
    }

    private int countTests(java.util.function.Predicate<QaCatalogRegistry.TestMapping> filter) {
        return QaCatalogRegistry.FEATURES.stream().flatMap(feature -> feature.tests().stream()).filter(filter)
                .mapToInt(QaCatalogRegistry.TestMapping::caseCount).sum();
    }

    private boolean isRegisteredClass(String className) {
        return FRONTEND_CLASSES.contains(className) || QaCatalogRegistry.FEATURES.stream()
                .flatMap(feature -> feature.tests().stream()).anyMatch(test -> test.testClass().equals(className));
    }

    private static String classPart(String testId) {
        if (testId == null) return "";
        String value = testId.split("#", 2)[0];
        return value.substring(value.lastIndexOf('.') + 1);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
