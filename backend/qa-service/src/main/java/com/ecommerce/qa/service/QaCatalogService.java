package com.ecommerce.qa.service;

import com.ecommerce.qa.api.QaModels;
import com.ecommerce.qa.persistence.QaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class QaCatalogService {
    private final QaRepository repository;

    public QaCatalogService(QaRepository repository) { this.repository = repository; }

    public QaModels.Overview overview() { return repository.overview(); }

    public QaModels.Page<QaModels.Feature> features(int page, int size, String search, String service, String coverage) {
        validatePage(page, size);
        return repository.features(page, size, search, service, coverage);
    }

    public QaModels.Feature feature(String featureId) {
        return repository.feature(featureId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature not found."));
    }

    public QaModels.Page<QaModels.TestCase> tests(int page, int size, String service, String layer, String featureId) {
        validatePage(page, size);
        return repository.tests(page, size, service, layer, featureId);
    }

    public List<QaModels.Suite> suites() { return repository.suites(); }

    public List<QaModels.RbacRole> roles() { return repository.roles(); }

    public List<QaModels.RbacCell> rbacMatrix() {
        List<QaModels.Feature> features = repository.allFeatures();
        List<QaModels.RbacRole> roles = repository.roles();
        List<QaModels.RbacCell> matrix = new ArrayList<>();
        for (QaModels.Feature feature : features) {
            if (feature.permissions().isEmpty()) {
                for (QaModels.RbacRole role : roles) matrix.add(new QaModels.RbacCell(role.key(), feature.id(), feature.name(),
                        "UNRESOLVED", feature.endpoints(), null, "POLICY_NOT_MAPPED"));
                continue;
            }
            for (QaModels.RbacRole role : roles) {
                for (String permission : feature.permissions()) {
                    boolean allowed = role.permissions().contains("*") || role.permissions().contains(permission);
                    matrix.add(new QaModels.RbacCell(role.key(), feature.id(), feature.name(), permission,
                            feature.endpoints(), allowed, repository.permissionTestCoverage(role.key(), permission)));
                }
            }
        }
        return matrix;
    }

    public QaModels.Coverage coverage() {
        List<QaModels.CoverageFeature> features = repository.coverage();
        int tests = features.stream().mapToInt(QaModels.CoverageFeature::registeredTests).sum();
        int endpoints = features.stream().mapToInt(QaModels.CoverageFeature::discoveredEndpoints).sum();
        return new QaModels.Coverage(features, tests, endpoints, "NOT_COLLECTED", "NOT_REGISTERED");
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || page > 10_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be between 0 and 10000.");
        if (size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100.");
    }
}
