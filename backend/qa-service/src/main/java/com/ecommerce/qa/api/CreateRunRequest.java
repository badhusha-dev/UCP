package com.ecommerce.qa.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record CreateRunRequest(String suiteId, String featureId, @NotBlank String environment) {
    @AssertTrue(message = "Choose exactly one approved suite or one catalog feature.")
    public boolean isScopeValid() {
        return (suiteId != null && !suiteId.isBlank()) ^ (featureId != null && !featureId.isBlank());
    }
}
