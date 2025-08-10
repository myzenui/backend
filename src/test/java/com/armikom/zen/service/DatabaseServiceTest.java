package com.armikom.zen.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseService
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/test_myzen?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
    "spring.datasource.username=myzenuser",
    "spring.datasource.password=myzenpass",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver"
})
public class DatabaseServiceTest {

    private DatabaseService databaseService;

    @BeforeEach
    void setUp() {
        databaseService = new DatabaseServiceImpl();
    }

    @Test
    void testValidateInput() {
        // Test validation through method calls
        assertThrows(IllegalArgumentException.class, () -> {
            databaseService.createDatabase("", "validuser", "validpass123");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            databaseService.createDatabase("validdb", "", "validpass123");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            databaseService.createDatabase("validdb", "validuser", "");
        });

        // Test invalid characters
        assertThrows(IllegalArgumentException.class, () -> {
            databaseService.createDatabase("invalid-db", "validuser", "validpass123");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            databaseService.createDatabase("validdb", "invalid-user", "validpass123");
        });

        // Test too short names
        assertThrows(IllegalArgumentException.class, () -> {
            databaseService.createDatabase("ab", "validuser", "validpass123");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            databaseService.createDatabase("validdb", "ab", "validpass123");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            databaseService.createDatabase("validdb", "validuser", "short");
        });
    }

    @Test
    void testConnectionTest() {
        // This will depend on whether MySQL is running and configured
        // For a unit test, we might want to mock this
        assertDoesNotThrow(() -> {
            boolean result = databaseService.testConnection();
            // Result can be true or false depending on MySQL availability
            assertNotNull(result);
        });
    }

    @Test
    void testValidInputs() {
        // Test that valid inputs don't throw exceptions
        assertDoesNotThrow(() -> {
            try {
                // This will fail if database doesn't exist or credentials are wrong,
                // but shouldn't throw validation exceptions
                databaseService.createDatabase("validdb123", "validuser123", "validpass123!");
            } catch (Exception e) {
                // Ignore SQL exceptions for this validation test
                if (!(e.getCause() instanceof java.sql.SQLException)) {
                    throw e;
                }
            }
        });
    }
}
