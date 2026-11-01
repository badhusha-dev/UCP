package com.ecommerce.qa.runner;

import java.util.List;

public record QaExecutionPlan(List<Step> steps, int expectedCases, String scopeLabel) {
    public QaExecutionPlan {
        steps = List.copyOf(steps);
    }

    public record Step(String key, String label, String module, List<String> testClasses, boolean frontend) {
        public Step { testClasses = List.copyOf(testClasses); }
    }
}
