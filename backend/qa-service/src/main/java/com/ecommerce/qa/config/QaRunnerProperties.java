package com.ecommerce.qa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "qa.runner")
public record QaRunnerProperties(
        String workspaceRoot,
        String workDirectory,
        String mavenExecutable,
        String nodeExecutable,
        long timeoutSeconds) {
    public Path sourceRoot() { return Path.of(workspaceRoot == null ? "" : workspaceRoot).toAbsolutePath().normalize(); }
    public Path workRoot() { return Path.of(workDirectory == null ? "" : workDirectory).toAbsolutePath().normalize(); }
}
