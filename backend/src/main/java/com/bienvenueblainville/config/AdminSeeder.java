package com.bienvenueblainville.config;

import com.bienvenueblainville.common.Role;
import com.bienvenueblainville.user.AppUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminSeeder(
            AppUserMapper appUserMapper,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email}") String adminEmail,
            @Value("${app.admin.password}") String adminPassword
    ) {
        this.appUserMapper = appUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (appUserMapper.countByRole(Role.ADMIN) > 0) {
            return;
        }

        appUserMapper.insert(adminEmail.trim().toLowerCase(), passwordEncoder.encode(adminPassword), Role.ADMIN);
        log.info("Seeded initial ADMIN account for {}", adminEmail);
    }
}
