package com.expensetracker.config;

import com.expensetracker.entity.Role;
import com.expensetracker.repository.RoleRepository;
import com.expensetracker.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(String... args) {

        seedRole(SecurityConstants.ROLE_USER, "Default user role");
        seedRole(SecurityConstants.ROLE_ADMIN, "Administrator role");
    }

    private void seedRole(String roleKey, String description) {

        if (!roleRepository.existsByRoleKey(roleKey)) {

            roleRepository.save(Role.builder()
                    .roleKey(roleKey)
                    .description(description)
                    .build());
        }
    }
}
