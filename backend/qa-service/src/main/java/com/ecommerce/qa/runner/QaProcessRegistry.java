package com.ecommerce.qa.runner;

import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class QaProcessRegistry {
    private final ConcurrentMap<UUID, Process> active = new ConcurrentHashMap<>();

    public void register(UUID runId, Process process) { active.put(runId, process); }
    public void unregister(UUID runId) { active.remove(runId); }

    @PreDestroy
    public void cancelAll() { active.keySet().forEach(this::cancel); }

    public void cancel(UUID runId) {
        Process process = active.get(runId);
        if (process == null) return;
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }
}
