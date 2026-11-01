package com.ecommerce.qa.runner;

import com.ecommerce.qa.api.QaModels;
import com.ecommerce.qa.catalog.QaCatalogRegistry;
import com.ecommerce.qa.config.QaRunnerProperties;
import com.ecommerce.qa.persistence.QaRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
public class QaExecutionRunner {
    private static final int MAX_CAPTURE_BYTES = 160_000;
    private static final Pattern MAVEN_RUNNING = Pattern.compile("\\[INFO] Running ([A-Za-z0-9_.$]+)");
    private static final Pattern MAVEN_SUMMARY = Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");
    private static final Pattern TAP_RESULT = Pattern.compile("^(ok|not ok)\\s+\\d+\\s+-\\s+(.+?)(?:\\s+#.*)?$");
    private static final Set<String> EXCLUDED_DIRECTORY_NAMES = Set.of("target", ".git", ".idea", "config-repo", "node_modules", "dist", "build", "coverage");

    private final QaRepository repository;
    private final QaRunnerProperties properties;
    private final QaSuiteRegistry suites;
    private final QaProcessRegistry processes;
    private final QaLogRedactor redactor;

    public QaExecutionRunner(QaRepository repository, QaRunnerProperties properties, QaSuiteRegistry suites,
                             QaProcessRegistry processes, QaLogRedactor redactor) {
        this.repository = repository;
        this.properties = properties;
        this.suites = suites;
        this.processes = processes;
        this.redactor = redactor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterRestart() {
        repository.recoverInterruptedRuns();
    }

    public void cancel(UUID runId) { processes.cancel(runId); }

    public void execute(UUID runId, QaExecutionPlan plan) {
        Path runDirectory = properties.workRoot().resolve(runId.toString()).normalize();
        List<QaModels.Result> results = new ArrayList<>();
        StringBuilder log = new StringBuilder();
        int infrastructureFailures = 0;
        boolean timedOut = false;
        try {
            if (isCancelled(runId)) return;
            repository.start(runId);
            String preflightError = preflight(plan);
            if (preflightError != null) {
                QaModels.Run run = repository.run(runId).orElse(null);
                addSynthetic(runId, run == null ? null : run.featureId(), "qa-runner", "PREFLIGHT", "BLOCKED",
                        "Local isolated runner is ready", preflightError, "PREFLIGHT_BLOCKED", preflightError, results);
                repository.replaceResults(runId, results);
                repository.finish(runId, "BLOCKED", "BLOCKED", results.size(), results.size(), 0, 0, 0,
                        results.size(), 0, "PREFLIGHT_BLOCKED", preflightError, preflightError);
                return;
            }

            Files.createDirectories(runDirectory);
            Path backendWorkspace = runDirectory.resolve("backend");
            boolean backendCopied = false;
            int completedBase = 0;
            int passedBase = 0;
            int failedBase = 0;
            int skippedBase = 0;

            for (QaExecutionPlan.Step step : plan.steps()) {
                if (isCancelled(runId)) return;
                repository.markRunning(runId, step.key(), step.label(), plan.expectedCases());
                if (step.frontend()) {
                    CommandOutcome outcome = runFrontend(runId, runDirectory, step, completedBase, passedBase, failedBase, skippedBase);
                    log.append(outcome.log());
                    List<QaModels.Result> parsed = parseFrontendReport(runId, outcome.reportPath(), step);
                    results.addAll(parsed);
                    completedBase += parsed.size();
                    passedBase += count(parsed, "PASSED");
                    failedBase += count(parsed, "FAILED");
                    skippedBase += count(parsed, "SKIPPED");
                    if (outcome.timedOut()) {
                        timedOut = true;
                        infrastructureFailures++;
                        addSynthetic(runId, featureFor(step.testClasses().get(0)), "frontend", "FRONTEND_UNIT", "TIMED_OUT",
                                "Registered frontend test suite completes within the configured time limit", "Test process timed out",
                                "TIMEOUT", "Frontend test process exceeded the configured time limit.", results);
                    } else if (outcome.exitCode() != 0 && parsed.isEmpty()) {
                        infrastructureFailures++;
                        addSynthetic(runId, featureFor(step.testClasses().get(0)), "frontend", "FRONTEND_UNIT", "INFRASTRUCTURE_FAILURE",
                                "Node test runner starts and writes a JUnit report", "Exit code " + outcome.exitCode(),
                                "FRONTEND_RUNNER_FAILURE", "Frontend test runner did not produce parseable test results.", results);
                    } else if (parsed.isEmpty()) {
                        infrastructureFailures++;
                        addSynthetic(runId, featureFor(step.testClasses().get(0)), "frontend", "FRONTEND_UNIT", "INFRASTRUCTURE_FAILURE",
                                "Registered frontend test files produce JUnit cases", "No test cases were reported",
                                "EMPTY_TEST_REPORT", "The Node test runner completed without reportable test cases.", results);
                    }
                } else {
                    if (!backendCopied) {
                        copyBackendSource(backendWorkspace);
                        backendCopied = true;
                    }
                    CommandOutcome outcome = runBackend(runId, runDirectory, backendWorkspace, step,
                            completedBase, passedBase, failedBase, skippedBase);
                    log.append(outcome.log());
                    List<QaModels.Result> parsed = parseMavenReports(runId, backendWorkspace, step);
                    results.addAll(parsed);
                    completedBase += parsed.size();
                    passedBase += count(parsed, "PASSED");
                    failedBase += count(parsed, "FAILED");
                    skippedBase += count(parsed, "SKIPPED");
                    if (outcome.timedOut()) {
                        timedOut = true;
                        infrastructureFailures++;
                        addSynthetic(runId, featureFor(step.testClasses().isEmpty() ? "" : step.testClasses().get(0)), step.module(), "BACKEND", "TIMED_OUT",
                                "Registered Maven test suite completes within the configured time limit", "Test process timed out",
                                "TIMEOUT", "Maven test process exceeded the configured time limit.", results);
                    } else if (outcome.exitCode() != 0 && parsed.isEmpty()) {
                        infrastructureFailures++;
                        addSynthetic(runId, featureFor(step.testClasses().isEmpty() ? "" : step.testClasses().get(0)), step.module(), "BACKEND", "INFRASTRUCTURE_FAILURE",
                                "Maven discovers and runs the registered test classes", "Exit code " + outcome.exitCode(),
                                "BACKEND_RUNNER_FAILURE", "Maven did not produce parseable test results.", results);
                    } else if (parsed.isEmpty()) {
                        infrastructureFailures++;
                        addSynthetic(runId, featureFor(step.testClasses().isEmpty() ? "" : step.testClasses().get(0)), step.module(), "BACKEND", "INFRASTRUCTURE_FAILURE",
                                "Registered Maven test classes produce test cases", "No test cases were reported",
                                "EMPTY_TEST_REPORT", "Maven completed without reportable test cases.", results);
                    }
                }
                completedBase = countCompleted(results);
                passedBase = count(results, "PASSED");
                failedBase = count(results, "FAILED");
                skippedBase = count(results, "SKIPPED");
                if (isCancelled(runId)) return;
            }

            if (isCancelled(runId)) return;
            repository.setStatus(runId, "FINALIZING", "FINALIZING", null, null);
            repository.replaceResults(runId, results);
            int passed = count(results, "PASSED");
            int failed = count(results, "FAILED");
            int skipped = count(results, "SKIPPED");
            int blocked = count(results, "BLOCKED");
            String finalStatus = failed > 0 ? "FAILED" : (infrastructureFailures > 0 || blocked > 0 || timedOut) ? "BLOCKED" : "PASSED";
            String errorType = finalStatus.equals("FAILED") ? "ASSERTION_FAILURE" : finalStatus.equals("BLOCKED") ? "RUNNER_OR_ENVIRONMENT" : null;
            String error = finalStatus.equals("PASSED") ? null : summarizeFailures(results, infrastructureFailures);
            repository.addExecutionLog(runId, redactor.redactLog(log.toString()));
            repository.finish(runId, finalStatus, finalStatus, results.size(), results.size(), passed, failed, skipped,
                    blocked, infrastructureFailures, errorType, error, redactor.redactLog(log.toString()));
        } catch (Exception ex) {
            if (isCancelled(runId)) return;
            String message = redactor.redact(ex.getMessage());
            addSynthetic(runId, null, "qa-service", "RUNNER", "INFRASTRUCTURE_FAILURE",
                    "Test plan executes in the isolated workspace", message, "RUNNER_EXCEPTION", message, results);
            repository.replaceResults(runId, results);
            repository.finish(runId, "BLOCKED", "BLOCKED", results.size(), results.size(), count(results, "PASSED"),
                    count(results, "FAILED"), count(results, "SKIPPED"), count(results, "BLOCKED"),
                    Math.max(1, infrastructureFailures), "RUNNER_EXCEPTION", message, redactor.redactLog(log.toString()));
        } finally {
            processes.unregister(runId);
            deleteWorkspace(runDirectory);
        }
    }

    private String preflight(QaExecutionPlan plan) {
        Path root = properties.sourceRoot();
        if (properties.workspaceRoot() == null || properties.workspaceRoot().isBlank()) return "QA_WORKSPACE_ROOT is not configured.";
        if (!Files.isRegularFile(root.resolve("backend/pom.xml"))) return "The isolated backend source tree is unavailable.";
        boolean needsBackend = plan.steps().stream().anyMatch(step -> !step.frontend());
        boolean needsFrontend = plan.steps().stream().anyMatch(QaExecutionPlan.Step::frontend);
        if (needsBackend && !executableAvailable(properties.mavenExecutable())) return "The approved Maven runner is unavailable.";
        if (needsFrontend) {
            if (!Files.isDirectory(root.resolve("frontend/shared-library/tests"))) return "The registered frontend tests are unavailable.";
            if (!Files.isDirectory(root.resolve("frontend/node_modules"))) return "Frontend dependencies are not installed in this local workspace.";
            if (!executableAvailable(properties.nodeExecutable())) return "The approved Node.js runner is unavailable.";
        }
        try {
            Files.createDirectories(properties.workRoot());
            if (!Files.isWritable(properties.workRoot())) return "The isolated runner workspace is not writable.";
            if (properties.workRoot().toFile().getUsableSpace() < 300L * 1024 * 1024) return "Less than 300 MB is available for isolated build output.";
        } catch (Exception ex) {
            return "The isolated runner workspace could not be prepared.";
        }
        return null;
    }

    private CommandOutcome runBackend(UUID runId, Path runDirectory, Path backendWorkspace, QaExecutionPlan.Step step,
                                      int completed, int passed, int failed, int skipped) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(properties.mavenExecutable());
        command.addAll(List.of("-B", "-ntp", "-Dmaven.repo.local=" + mavenRepositoryPath(), "-f", backendWorkspace.resolve("pom.xml").toString()));
        if (!step.module().isBlank()) {
            command.addAll(List.of("-pl", step.module()));
            if (!step.testClasses().isEmpty()) {
                command.add("-Dtest=" + String.join(",", step.testClasses()));
                command.add("-Dsurefire.failIfNoSpecifiedTests=false");
            }
        }
        command.add("test");
        return executeCommand(runId, runDirectory, command, backendWorkspace, "backend-" + step.key(), completed, passed, failed, skipped);
    }

    private CommandOutcome runFrontend(UUID runId, Path runDirectory, QaExecutionPlan.Step step,
                                       int completed, int passed, int failed, int skipped) throws Exception {
        Path output = runDirectory.resolve("frontend-tests.xml");
        List<String> command = new ArrayList<>(List.of(properties.nodeExecutable(), "--experimental-strip-types", "--test",
                "--test-reporter=tap", "--test-reporter-destination=stdout", "--test-reporter=junit",
                "--test-reporter-destination=" + output));
        step.testClasses().forEach(file -> command.add("tests/" + file));
        Path sharedLibrary = properties.sourceRoot().resolve("frontend/shared-library");
        return executeCommand(runId, runDirectory, command, sharedLibrary, "frontend", completed, passed, failed, skipped, output);
    }

    private CommandOutcome executeCommand(UUID runId, Path runDirectory, List<String> command, Path workingDirectory,
                                          String stage, int baseCompleted, int basePassed, int baseFailed, int baseSkipped) throws Exception {
        return executeCommand(runId, runDirectory, command, workingDirectory, stage, baseCompleted, basePassed, baseFailed, baseSkipped, null);
    }

    private CommandOutcome executeCommand(UUID runId, Path runDirectory, List<String> command, Path workingDirectory,
                                          String stage, int baseCompleted, int basePassed, int baseFailed, int baseSkipped,
                                          Path reportPath) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile()).redirectErrorStream(true);
        java.util.Map<String, String> env = builder.environment();
        env.clear();
        String inheritedPath = System.getenv("PATH");
        if (inheritedPath != null) env.put("PATH", inheritedPath);
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) env.put("JAVA_HOME", javaHome);
        env.put("HOME", properties.workRoot().resolve("home").toString());
        env.put("CI", "true");
        env.put("TZ", "UTC");
        env.put("QA_ENVIRONMENT", "LOCAL_ISOLATED");
        env.put("MAVEN_OPTS", "-Xms128m -Xmx1024m -XX:MaxMetaspaceSize=384m");
        Files.createDirectories(Path.of(env.get("HOME")));
        Process process = builder.start();
        processes.register(runId, process);
        AtomicInteger completed = new AtomicInteger(baseCompleted);
        AtomicInteger passed = new AtomicInteger(basePassed);
        AtomicInteger failed = new AtomicInteger(baseFailed);
        AtomicInteger skipped = new AtomicInteger(baseSkipped);
        StringBuilder captured = new StringBuilder();
        Thread reader = new Thread(() -> collectOutput(process, captured, runId, stage, completed, passed, failed, skipped), "qa-output-" + runId);
        reader.setDaemon(true);
        reader.start();
        long timeout = Math.max(60, Math.min(properties.timeoutSeconds(), 3600));
        boolean completedInTime = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!completedInTime) terminateProcessTree(process);
        reader.join(5_000);
        processes.unregister(runId);
        String safeLog = redactor.redactLog(captured.toString());
        return new CommandOutcome(completedInTime ? process.exitValue() : -1, !completedInTime, safeLog, reportPath);
    }

    private void collectOutput(Process process, StringBuilder capture, UUID runId, String stage,
                               AtomicInteger completed, AtomicInteger passed, AtomicInteger failed,
                               AtomicInteger skipped) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendBounded(capture, line);
                Matcher running = MAVEN_RUNNING.matcher(line);
                if (running.find()) repository.updateProgress(runId, running.group(1), completed.get(), passed.get(), failed.get(), skipped.get());
                Matcher summary = MAVEN_SUMMARY.matcher(line);
                if (summary.find()) {
                    int tests = parseInt(summary.group(1));
                    int failures = parseInt(summary.group(2)) + parseInt(summary.group(3));
                    int skippedCases = parseInt(summary.group(4));
                    int passedCases = Math.max(0, tests - failures - skippedCases);
                    completed.addAndGet(tests);
                    passed.addAndGet(passedCases);
                    failed.addAndGet(failures);
                    skipped.addAndGet(skippedCases);
                    repository.updateProgress(runId, "" + stage + " test class completed", completed.get(), passed.get(), failed.get(), skipped.get());
                } else {
                    Matcher tap = TAP_RESULT.matcher(line.trim());
                    if (tap.matches()) {
                        completed.incrementAndGet();
                        if ("ok".equals(tap.group(1))) passed.incrementAndGet(); else failed.incrementAndGet();
                        repository.updateProgress(runId, tap.group(2), completed.get(), passed.get(), failed.get(), skipped.get());
                    }
                }
            }
        } catch (Exception ex) {
            appendBounded(capture, "[QA runner output could not be fully read]");
        }
    }

    private List<QaModels.Result> parseMavenReports(UUID runId, Path backendWorkspace, QaExecutionPlan.Step step) {
        List<QaModels.Result> rows = new ArrayList<>();
        try (var paths = Files.walk(backendWorkspace)) {
            for (Path report : paths.filter(path -> path.getFileName().toString().startsWith("TEST-")
                    && path.getFileName().toString().endsWith(".xml") && path.toString().contains("surefire-reports")).toList()) {
                String module = moduleFromReport(backendWorkspace, report);
                if (!step.module().isBlank() && !step.module().equals(module)) continue;
                Element root = parseXml(report);
                NodeList cases = root.getElementsByTagName("testcase");
                for (int index = 0; index < cases.getLength(); index++) {
                    Element testCase = (Element) cases.item(index);
                    String className = testCase.getAttribute("classname");
                    String name = testCase.getAttribute("name");
                    String status = "PASSED";
                    String message = null;
                    String actual = "All assertions passed.";
                    Element failure = child(testCase, "failure");
                    Element error = child(testCase, "error");
                    Element skippedCase = child(testCase, "skipped");
                    if (failure != null || error != null) {
                        status = "FAILED";
                        Element issue = failure != null ? failure : error;
                        message = redactor.redact(firstNonBlank(issue.getAttribute("message"), issue.getTextContent()));
                        actual = message;
                    } else if (skippedCase != null) {
                        status = "SKIPPED";
                        message = redactor.redact(skippedCase.getAttribute("message"));
                        actual = "Test was skipped";
                    }
                    long duration = Math.max(0, Math.round(parseDouble(testCase.getAttribute("time")) * 1000));
                    QaCatalogRegistry.TestMapping mapping = mappingFor(className);
                    String featureId = mapping == null ? QaCatalogRegistry.serviceForModule(module) : featureForClass(className);
                    String permission = permissionFor(name, mapping);
                    String roleName = roleFromTestName(name);
                    rows.add(new QaModels.Result(0, runId, className + "#" + name, name, featureId, module,
                            mapping == null ? "BACKEND" : mapping.layer(), roleName, permission, status, duration,
                            status.equals("FAILED") ? expectedFrom(message) : "Registered assertions complete successfully",
                            actual, message, null, "qa://runs/" + runId + "/report?format=junit", Instant.now()));
                }
            }
        } catch (Exception ex) {
            appendBounded(new StringBuilder(), "JUnit report parsing failed: " + ex.getMessage());
        }
        return rows;
    }

    private List<QaModels.Result> parseFrontendReport(UUID runId, Path report, QaExecutionPlan.Step step) {
        List<QaModels.Result> rows = new ArrayList<>();
        if (report == null || !Files.isRegularFile(report)) return rows;
        try {
            Element root = parseXml(report);
            NodeList cases = root.getElementsByTagName("testcase");
            for (int index = 0; index < cases.getLength(); index++) {
                Element testCase = (Element) cases.item(index);
                String className = firstNonBlank(testCase.getAttribute("classname"), "shared-library");
                String name = testCase.getAttribute("name");
                String status = "PASSED";
                String message = null;
                String actual = "All assertions passed.";
                Element failure = child(testCase, "failure");
                Element skippedCase = child(testCase, "skipped");
                if (failure != null) {
                    status = "FAILED";
                    message = redactor.redact(firstNonBlank(failure.getAttribute("message"), failure.getTextContent()));
                    actual = message;
                } else if (skippedCase != null) {
                    status = "SKIPPED";
                    message = redactor.redact(skippedCase.getAttribute("message"));
                    actual = "Test was skipped";
                }
                String featureId = className.contains("cartSlice") ? "cart" : className.contains("authSlice") ? "users" : "quality-center";
                rows.add(new QaModels.Result(0, runId, className + "#" + name, name, featureId, "shared-library",
                        "FRONTEND_UNIT", null, null, status, Math.max(0, Math.round(parseDouble(testCase.getAttribute("time")) * 1000)),
                        status.equals("FAILED") ? expectedFrom(message) : "Registered assertions complete successfully", actual,
                        message, null, "qa://runs/" + runId + "/report?format=junit", Instant.now()));
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return rows;
    }

    private Element parseXml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
    }

    private void copyBackendSource(Path destination) throws Exception {
        Path source = properties.sourceRoot().resolve("backend");
        Files.createDirectories(destination);
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                if (path.equals(source)) continue;
                Path relative = source.relativize(path);
                boolean excluded = false;
                for (Path part : relative) if (EXCLUDED_DIRECTORY_NAMES.contains(part.toString())) { excluded = true; break; }
                if (excluded || Files.isSymbolicLink(path)) continue;
                Path target = destination.resolve(relative.toString());
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else if (Files.isRegularFile(path)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target);
                }
            }
        }
    }

    private void addSynthetic(UUID runId, String featureId, String service, String layer, String status,
                              String expected, String actual, String errorType, String errorMessage,
                              List<QaModels.Result> results) {
        results.add(new QaModels.Result(0, runId, "qa-runner#" + errorType, errorType, featureId, service, layer,
                null, null, status, 0, expected, redactor.redact(actual), redactor.redact(errorMessage),
                null, null, Instant.now()));
    }

    private String moduleFromReport(Path root, Path report) {
        Path relative = root.relativize(report);
        return relative.getNameCount() > 0 ? relative.getName(0).toString() : "backend";
    }

    private QaCatalogRegistry.TestMapping mappingFor(String className) {
        String shortName = className.substring(className.lastIndexOf('.') + 1);
        return QaCatalogRegistry.FEATURES.stream().flatMap(feature -> feature.tests().stream())
                .filter(test -> test.testClass().equals(shortName)).findFirst().orElse(null);
    }

    private String featureForClass(String className) {
        QaCatalogRegistry.Feature feature = QaCatalogRegistry.featureForClass(className);
        return feature == null ? "quality-center" : feature.id();
    }

    private String featureFor(String testClass) { return featureForClass(testClass); }
    private String permissionFor(String name, QaCatalogRegistry.TestMapping mapping) {
        if (name == null) return mapping == null ? null : mapping.permission();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("cancel")) return "qa:cancel";
        if (lower.contains("run") || lower.contains("start")) return "qa:run";
        if (lower.contains("read") || lower.contains("view")) return "qa:read";
        return mapping == null ? null : mapping.permission();
    }
    private static int count(List<QaModels.Result> rows, String status) { return (int) rows.stream().filter(row -> status.equals(row.status())).count(); }
    private static int countCompleted(List<QaModels.Result> rows) { return rows.size(); }
    private static int parseInt(String value) { try { return Integer.parseInt(value); } catch (Exception ex) { return 0; } }
    private static double parseDouble(String value) { try { return Double.parseDouble(value); } catch (Exception ex) { return 0; } }
    private static Element child(Element parent, String tag) { NodeList nodes = parent.getElementsByTagName(tag); return nodes.getLength() == 0 ? null : (Element) nodes.item(0); }
    private static String firstNonBlank(String first, String second) { return first == null || first.isBlank() ? second : first; }
    private static String expectedFrom(String message) {
        if (message == null || message.isBlank()) return "Test assertions pass";
        Matcher match = Pattern.compile("(?s)expected:\\s*<?(.*?)>?\\s+but was:<?(.*?)>?$").matcher(message);
        return match.find() ? "Expected " + match.group(1).trim() : "Test assertion passes";
    }
    private static String roleFromTestName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String role : List.of("super_admin", "admin", "manager", "accountant", "auditor", "user")) {
            if (lower.contains(role)) return role.toUpperCase(Locale.ROOT);
        }
        return null;
    }
    private static String summarizeFailures(List<QaModels.Result> results, int infrastructureFailures) {
        List<String> failures = results.stream().filter(result -> "FAILED".equals(result.status()) || "BLOCKED".equals(result.status())
                        || "INFRASTRUCTURE_FAILURE".equals(result.status()) || "TIMED_OUT".equals(result.status()))
                .limit(3).map(result -> result.testName() + (result.errorMessage() == null ? "" : ": " + result.errorMessage())).toList();
        String summary = String.join("; ", failures);
        if (infrastructureFailures > 0 && summary.isBlank()) summary = "One or more suites could not produce test results.";
        return summary.isBlank() ? null : summary;
    }
    private String mavenRepositoryPath() { return properties.workRoot().resolve("home/.m2/repository").toString(); }
    private boolean isCancelled(UUID runId) { return repository.run(runId).map(run -> "CANCELLED".equals(run.status())).orElse(true); }

    private boolean executableAvailable(String executable) {
        if (executable == null || executable.isBlank()) return false;
        Path direct = Path.of(executable);
        if (direct.isAbsolute() || executable.contains("/") || executable.contains("\\")) return Files.isRegularFile(direct) && Files.isExecutable(direct);
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String directory : path.split(java.util.regex.Pattern.quote(System.getProperty("path.separator")))) {
            Path candidate = Path.of(directory).resolve(executable);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return true;
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                for (String extension : List.of(".exe", ".cmd", ".bat")) {
                    Path withExtension = Path.of(directory).resolve(executable + extension);
                    if (Files.isRegularFile(withExtension)) return true;
                }
            }
        }
        return false;
    }

    private void appendBounded(StringBuilder builder, String line) {
        synchronized (builder) {
            if (builder.length() > MAX_CAPTURE_BYTES) return;
            builder.append(line).append('\n');
            if (builder.length() > MAX_CAPTURE_BYTES) builder.append("[Output truncated]\n");
        }
    }

    private void deleteWorkspace(Path path) {
        if (path == null || !path.startsWith(properties.workRoot()) || !Files.exists(path)) return;
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try { Files.deleteIfExists(candidate); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    private void terminateProcessTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private record CommandOutcome(int exitCode, boolean timedOut, String log, Path reportPath) {}
}
