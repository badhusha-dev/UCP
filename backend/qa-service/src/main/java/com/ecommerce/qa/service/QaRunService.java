package com.ecommerce.qa.service;

import com.ecommerce.qa.api.CreateRunRequest;
import com.ecommerce.qa.api.QaModels;
import com.ecommerce.qa.persistence.QaRepository;
import com.ecommerce.qa.runner.QaExecutionPlan;
import com.ecommerce.qa.runner.QaExecutionRunner;
import com.ecommerce.qa.runner.QaSuiteRegistry;
import com.ecommerce.qa.security.QaPermissionService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class QaRunService {
    private static final int MAX_RUNS_PER_HOUR = 5;
    private static final String ENVIRONMENT = "LOCAL_ISOLATED";

    private final QaRepository repository;
    private final QaSuiteRegistry suites;
    private final QaExecutionRunner runner;
    private final Executor executor;

    public QaRunService(QaRepository repository, QaSuiteRegistry suites, QaExecutionRunner runner,
                        @org.springframework.beans.factory.annotation.Qualifier("qaRunExecutor") Executor executor) {
        this.repository = repository;
        this.suites = suites;
        this.runner = runner;
        this.executor = executor;
    }

    @Transactional
    public QaModels.Run start(CreateRunRequest request, QaPermissionService.Actor actor) {
        if (!ENVIRONMENT.equalsIgnoreCase(request.environment())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only the LOCAL_ISOLATED environment is allowed for QA execution.");
        }
        QaExecutionPlan plan = suites.resolve(request.suiteId(), request.featureId());
        return create(plan, request.suiteId() == null ? "feature" : request.suiteId().toLowerCase(),
                request.featureId(), actor, null);
    }

    @Transactional
    public QaModels.Run rerunFailures(UUID originalRunId, QaPermissionService.Actor actor) {
        QaModels.Run original = repository.run(originalRunId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test run not found."));
        List<String> failedIds = repository.failedResults(originalRunId).stream().map(QaModels.Result::testId).toList();
        QaExecutionPlan plan = suites.retryFailures(failedIds);
        return create(plan, "retry-failures", original.featureId(), actor, originalRunId);
    }

    @Transactional
    public QaModels.Run cancel(UUID runId, QaPermissionService.Actor actor) {
        QaModels.Run current = repository.run(runId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test run not found."));
        if (!repository.cancel(runId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This test run is already terminal and cannot be cancelled.");
        }
        repository.audit(actor.id(), actor.email(), "RUN_CANCELLED", runId, current.status());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { runner.cancel(runId); }
        });
        return repository.run(runId).orElseThrow();
    }

    private QaModels.Run create(QaExecutionPlan plan, String suiteId, String featureId,
                                QaPermissionService.Actor actor, UUID retryOf) {
        if (repository.countRecentRuns(actor.id(), Instant.now().minusSeconds(3600)) >= MAX_RUNS_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "The run limit is five per user per hour.");
        }
        UUID id = UUID.randomUUID();
        QaModels.Run run = new QaModels.Run(id, suiteId, featureId, ENVIRONMENT, sourceVersion(), actor.id(), actor.email(),
                "QUEUED", "QUEUED", null, plan.expectedCases(), 0, 0, 0, 0, 0, 0,
                null, null, Instant.now(), null, null, null, retryOf);
        try {
            repository.createRun(run);
            if (!repository.acquireRunLock(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Another QA run is active. Wait for it to finish before starting another.");
            }
            repository.audit(actor.id(), actor.email(), "RUN_REQUESTED", id,
                    "suite=" + suiteId + "; feature=" + (featureId == null ? "" : featureId) + "; environment=" + ENVIRONMENT);
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A QA run could not reserve the execution slot.");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    executor.execute(() -> runner.execute(id, plan));
                } catch (RuntimeException ex) {
                    repository.finish(id, "BLOCKED", "BLOCKED", plan.expectedCases(), 0, 0, 0, 0, 1, 1,
                            "RUNNER_QUEUE_FULL", "The bounded QA worker is not available.", "");
                }
            }
        });
        return run;
    }

    private static String sourceVersion() {
        String version = System.getenv("QA_SOURCE_VERSION");
        return version == null || version.isBlank() ? "local-source-version-unavailable" : version.substring(0, Math.min(version.length(), 96));
    }
}
