package com.expensetracker.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final Cache<String, Integer> attempts;

    public LoginAttemptService(
            @Value("${app.security.login.max-attempts:5}") int maxAttempts,
            @Value("${app.security.login.window-minutes:1}") int windowMinutes) {

        this.maxAttempts = maxAttempts;
        this.attempts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(windowMinutes))
                .maximumSize(100_000)
                .build();
    }

    public boolean isBlocked(String key) {
        Integer count = attempts.getIfPresent(key);
        return count != null && count >= maxAttempts;
    }

    public void recordFailure(String key) {
        attempts.asMap().merge(key, 1, Integer::sum);
    }

    public void reset(String key) {
        attempts.invalidate(key);
    }
}
