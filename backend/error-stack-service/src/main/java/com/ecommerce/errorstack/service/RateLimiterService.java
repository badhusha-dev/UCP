package com.ecommerce.errorstack.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimiterService {

    private static final int MAX_PER_WINDOW = 300; // max errors per window per service
    private static final long WINDOW_MILLIS = 60_000L; // 1 minute window

    private static class WindowCounter {
        long windowStart;
        AtomicInteger count;

        WindowCounter(long start) {
            this.windowStart = start;
            this.count = new AtomicInteger(1);
        }
    }

    private final ConcurrentHashMap<String, WindowCounter> rateLimits = new ConcurrentHashMap<>();

    public boolean tryAcquire(String serviceKey) {
        String key = serviceKey != null ? serviceKey.trim().toLowerCase() : "default";
        long now = System.currentTimeMillis();

        WindowCounter counter = rateLimits.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart > WINDOW_MILLIS) {
                return new WindowCounter(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        return counter.count.get() <= MAX_PER_WINDOW;
    }
}
