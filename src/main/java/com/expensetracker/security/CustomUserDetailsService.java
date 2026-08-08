package com.expensetracker.security;

import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final long CACHE_MAX_SIZE = 10_000;

    private final UserRepository userRepository;

    private final Cache<String, CustomUserDetails> userCache =
            Caffeine.newBuilder()
                    .expireAfterWrite(CACHE_TTL)
                    .maximumSize(CACHE_MAX_SIZE)
                    .build();

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        String normalizedEmail = normalizeEmail(email);

        CustomUserDetails cached = userCache.getIfPresent(normalizedEmail);
        if (cached != null) {
            return cached;
        }

        User user = userRepository.findByEmailWithRoles(normalizedEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Invalid credentials"));

        CustomUserDetails userDetails = new CustomUserDetails(user);
        userCache.put(normalizedEmail, userDetails);

        return userDetails;
    }

    public void evict(String email) {
        if (email != null) {
            userCache.invalidate(normalizeEmail(email));
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
