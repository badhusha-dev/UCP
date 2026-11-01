package com.ecommerce.qa.runner;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QaRunSelectionTest {
    private final QaSuiteRegistry registry = new QaSuiteRegistry();

    @Test
    void allSuiteContainsOnlyTheRegisteredBackendAndFrontendSteps() {
        QaExecutionPlan plan = registry.resolve("all", null);
        assertThat(plan.steps()).extracting(QaExecutionPlan.Step::key).containsExactly("backend", "frontend");
        assertThat(plan.expectedCases()).isGreaterThan(100);
    }

    @Test
    void rbacSuiteSelectsGatewayAndServicePermissionTests() {
        QaExecutionPlan plan = registry.resolve("rbac", null);
        assertThat(plan.steps()).extracting(QaExecutionPlan.Step::module).containsExactly("api-gateway", "qa-service");
        assertThat(plan.steps()).flatExtracting(QaExecutionPlan.Step::testClasses)
                .containsExactly("QualityCenterRbacTest", "QaPermissionServiceTest");
        assertThat(plan.expectedCases()).isEqualTo(11);
    }

    @Test
    void featureSelectionResolvesOnlyTheFeatureClassesInTheAllowList() {
        QaExecutionPlan plan = registry.resolve(null, "cart");
        assertThat(plan.steps()).extracting(QaExecutionPlan.Step::key).containsExactly("cart-service", "frontend");
        assertThat(plan.steps()).flatExtracting(QaExecutionPlan.Step::testClasses)
                .contains("CartServiceIntegrationTest", "cartSlice.test.ts");
    }

    @Test
    void featureWithoutTestsIsRejectedInsteadOfReportedAsPassing() {
        assertThatThrownBy(() -> registry.resolve(null, "procurement"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void unregisteredBrowserE2eSuiteIsRejectedClearly() {
        assertThatThrownBy(() -> registry.resolve("e2e", null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
