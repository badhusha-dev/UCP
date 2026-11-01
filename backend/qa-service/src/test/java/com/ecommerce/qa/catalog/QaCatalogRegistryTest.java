package com.ecommerce.qa.catalog;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QaCatalogRegistryTest {
    @Test
    void featureIdsAreUniqueAndStable() {
        Set<String> ids = new HashSet<>();
        QaCatalogRegistry.FEATURES.forEach(feature -> assertThat(ids.add(feature.id())).isTrue());
        assertThat(ids).contains("accounting", "crm", "procurement", "rbac", "quality-center");
    }

    @Test
    void testMappingIdsAreUniqueWithinEachFeature() {
        QaCatalogRegistry.FEATURES.forEach(feature -> {
            Set<String> ids = new HashSet<>();
            feature.tests().forEach(test -> {
                assertThat(ids.add(test.testId())).isTrue();
                assertThat(test.testClass()).isNotBlank();
                assertThat(test.caseCount()).isPositive();
            });
        });
    }

    @Test
    void featureCoverageIsNeverLabeledCompleteFromRegistryPresenceAlone() {
        assertThat(QaCatalogRegistry.FEATURES).allSatisfy(feature ->
                assertThat(feature.coverageStatus()).isIn("PARTIAL", "NOT_COVERED"));
        assertThat(QaCatalogRegistry.FEATURES.stream().filter(f -> f.coverageStatus().equals("NOT_COVERED")))
                .extracting(QaCatalogRegistry.Feature::id).contains("procurement");
    }

    @Test
    void endpointListsComeFromServiceRouteFamilies() {
        assertThat(QaCatalogRegistry.FEATURES.stream().flatMap(feature -> feature.endpoints().stream()))
                .contains("/api/accounting/**", "/api/crm/**", "/api/procurement/**", "/api/admin/qa/**");
    }

    @Test
    void defaultManagerDoesNotReceivePrivilegedQaPermissions() {
        QaCatalogRegistry.Role manager = QaCatalogRegistry.DEFAULT_ROLES.stream()
                .filter(role -> role.key().equals("MANAGER")).findFirst().orElseThrow();
        assertThat(manager.allows("qa:read")).isFalse();
        assertThat(manager.allows("qa:run")).isFalse();
        assertThat(QaCatalogRegistry.DEFAULT_ROLES.stream().filter(role -> role.key().equals("SUPER_ADMIN"))
                .findFirst().orElseThrow().allows("qa:cancel")).isTrue();
    }
}
