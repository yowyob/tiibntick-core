package com.yowyob.tiibntick.core.gofreelancer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Bootstrap configuration to ensure the admin user exists with the correct
 * credentials provided in the application.properties (or environment variables).
 * 
 * If no variables are provided, it falls back to admin@test.com / admin123.
 */
@Slf4j
@Component
public class AdminBootstrapConfig implements ApplicationRunner {

    private final DatabaseClient databaseClient;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:admin@test.com}")
    private String adminEmail;

    @Value("${admin.password:admin123}")
    private String adminPassword;

    public AdminBootstrapConfig(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
        // Instantiating locally to avoid dependency on global beans in this module
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=".repeat(60));
        log.info("Checking/Creating admin user with email: {}", adminEmail);

        String hashedPassword = passwordEncoder.encode(adminPassword);
        // Fixed ID to match the Liquibase script and avoid duplicates
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // Insert or Update the admin in the 'persons' table
        String sql = """
            INSERT INTO persons (id, first_name, last_name, email, phone, password, national_id, photo_card, role, is_active)
            VALUES (:id, 'Admin', 'User', :email, '+000000000001', :password, 'ADMIN_NATIONAL_ID', 'ADMIN_PHOTO_CARD_URL', 'ADMIN', true)
            ON CONFLICT (email) DO UPDATE 
            SET role = 'ADMIN', password = :password, is_active = true
            """;

        databaseClient.sql(sql)
            .bind("id", adminId)
            .bind("email", adminEmail)
            .bind("password", hashedPassword)
            .fetch()
            .rowsUpdated()
            .doOnSuccess(rows -> {
                log.info("Admin user ready. Rows updated in 'persons': {}", rows);
                log.info("=".repeat(60));
            })
            .doOnError(error -> {
                log.error("Failed to initialize admin user: {}", error.getMessage());
            })
            .subscribe();
    }
}
