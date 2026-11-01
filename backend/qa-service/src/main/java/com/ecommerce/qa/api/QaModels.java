package com.ecommerce.qa.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QaModels {
    private QaModels() {}

    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
    public record TestMapping(String testId, String name, String testClass, String layer, int caseCount, String permission) {}
    public record Feature(String id, String name, String domain, String frontendModule, String backendService,
                          List<String> endpoints, List<String> databases, List<String> permissions,
                          String coverageStatus, String coverageNote, int automatedTestCount,
                          String lastRunStatus, Instant lastRunAt, List<TestMapping> tests) {}
    public record Suite(String id, String name, String description, boolean executable) {}
    public record TestCase(String id, String name, String featureId, String featureName,
                           String serviceName, String layer, String permission,
                           int registeredCaseCount, String coverageStatus) {}
    public record Run(UUID id, String suiteId, String featureId, String environment, String sourceVersion,
                      long requestedBy, String requestedByEmail, String status, String phase,
                      String currentTest, int totalTests, int completedTests, int passedTests,
                      int failedTests, int skippedTests, int blockedTests, int infrastructureFailures,
                      String errorType, String errorMessage, Instant createdAt, Instant startedAt,
                      Instant finishedAt, Long durationMs, UUID retryOf) {}
    public record Result(long id, UUID runId, String testId, String testName, String featureId,
                         String serviceName, String layer, String roleName, String permission,
                         String status, long durationMs, String expected, String actual,
                         String errorMessage, String logExcerpt, String evidenceReference, Instant executedAt) {}
    public record Overview(long totalFeatures, long featuresWithTests, long featuresWithoutTests,
                           long registeredTestCases, long totalDiscoveredEndpoints,
                           String endpointCoverageStatus, String latestRunStatus,
                           int passed, int failed, int skipped, int blocked, int infrastructureFailures,
                           String rbacCoverageStatus, long registeredRbacCases, long lastRunRbacCases,
                           String lastSuccessfulFullRunAt, List<Run> recentRuns) {}
    public record RbacRole(String key, String name, List<String> permissions) {}
    public record RbacCell(String role, String featureId, String featureName, String permission,
                           List<String> endpoints, Boolean allowed, String testCoverage) {}
    public record CoverageFeature(String featureId, String name, String backendService,
                                  int registeredTests, String functionalCoverage,
                                  int discoveredEndpoints, String endpointCoverage,
                                  int rbacTests, String rbacCoverage) {}
    public record Coverage(List<CoverageFeature> features, int registeredTestCases,
                           int endpointsDiscovered, String codeCoverageStatus,
                           String browserE2eStatus) {}
    public record Artifact(String type, String name, boolean available, String format) {}
    public record RunLog(java.util.UUID runId, String log) {}
    public record Report(Run run, List<Result> results, List<Result> rbacResults) {}
}
