package com.expensetracker.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TokenDenylistService {

    private static final Duration CLEANUP_BACKSTOP = Duration.ofDays(1);
    private static final long MAX_SIZE = 100_000;

    private final Cache<String, Long> denylist =
            Caffeine.newBuilder()
                    .expireAfterAccess(CLEANUP_BACKSTOP)
                    .maximumSize(MAX_SIZE)
                    .build();

    public void deny(String jti, Duration ttl) {
        if (jti != null) {
            denylist.put(jti, System.currentTimeMillis() + ttl.toMillis());
        }
    }

    public boolean isDenied(String jti) {
        if (jti == null) {
            return false;
        }
        Long expiry = denylist.getIfPresent(jti);
        return expiry != null && expiry > System.currentTimeMillis();
    }
}
