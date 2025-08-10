package com.armikom.zen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * MySQL implementation of DatabaseService for handling database operations
 */
@Service
public class DatabaseServiceImpl implements DatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseServiceImpl.class);

    // Patterns for input validation (allow hyphen to support project ids like "tour-buddy")
    private static final Pattern VALID_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,64}$");
    private static final Pattern VALID_PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9@#$%^&+=!-]{8,128}$");
    private static final Pattern VALID_DB_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,64}$");

    @Value("${spring.datasource.url}")
    private String connectionString;

    @Value("${spring.datasource.username}")
    private String adminUsername;

    @Value("${spring.datasource.password}")
    private String adminPassword;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    /**
     * Gets a connection to MySQL server (without specifying a database)
     */
    private Connection getServerConnection() throws SQLException {
        // Extract server URL without database name
        String serverUrl = connectionString.substring(0, connectionString.lastIndexOf('/'));
        return DriverManager.getConnection(serverUrl + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", 
                                         adminUsername, adminPassword);
    }

    /**
     * Gets a connection to a specific database
     */
    private Connection getDatabaseConnection(String databaseName) throws SQLException {
        validateDatabaseName(databaseName);
        String serverUrl = connectionString.substring(0, connectionString.lastIndexOf('/'));
        return DriverManager.getConnection(serverUrl + "/" + databaseName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", 
                                         adminUsername, adminPassword);
    }

    @Override
    public boolean createDatabase(String databaseName, String username, String password) throws SQLException {
        validateDatabaseName(databaseName);
        validateUsername(username);
        validatePassword(password);

        try (Connection connection = getServerConnection()) {
            // Create database
            String createDbSql = "CREATE DATABASE IF NOT EXISTS " + escapeIdentifier(databaseName) + 
                               " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
            
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(createDbSql);
                logger.info("Database '{}' created successfully", databaseName);
            }

            // Create user and grant privileges
            String createUserSql = "CREATE USER IF NOT EXISTS ?@'%' IDENTIFIED BY ?";
            try (PreparedStatement pstmt = connection.prepareStatement(createUserSql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, password);
                pstmt.executeUpdate();
                logger.info("User '{}' created successfully", username);
            }

            // Grant all privileges on the database to the user
            String grantSql = "GRANT ALL PRIVILEGES ON " + escapeIdentifier(databaseName) + ".* TO ?@'%'";
            try (PreparedStatement pstmt = connection.prepareStatement(grantSql)) {
                pstmt.setString(1, username);
                pstmt.executeUpdate();
                logger.info("Privileges granted to user '{}' on database '{}'", username, databaseName);
            }

            // Flush privileges
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("FLUSH PRIVILEGES");
            }

            return true;
        } catch (SQLException e) {
            logger.error("Failed to create database '{}' with user '{}'", databaseName, username, e);
            throw e;
        }
    }

    @Override
    public boolean trimAllTables(String databaseName) throws SQLException {
        validateDatabaseName(databaseName);

        try (Connection connection = getDatabaseConnection(databaseName)) {
            // Get all table names
            List<String> tableNames = new ArrayList<>();
            String getTablesSql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ?";
            
            try (PreparedStatement pstmt = connection.prepareStatement(getTablesSql)) {
                pstmt.setString(1, databaseName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        tableNames.add(rs.getString("table_name"));
                    }
                }
            }

            if (tableNames.isEmpty()) {
                logger.info("No tables found in database '{}'", databaseName);
                return true;
            }

            // Disable foreign key checks
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 0");
            }

            // Truncate all tables
            try (Statement stmt = connection.createStatement()) {
                for (String tableName : tableNames) {
                    String truncateSql = "TRUNCATE TABLE " + escapeIdentifier(tableName);
                    stmt.executeUpdate(truncateSql);
                    logger.debug("Truncated table '{}'", tableName);
                }
            }

            // Re-enable foreign key checks
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 1");
            }

            logger.info("Successfully truncated {} tables in database '{}'", tableNames.size(), databaseName);
            return true;

        } catch (SQLException e) {
            logger.error("Failed to truncate tables in database '{}'", databaseName, e);
            throw e;
        }
    }

    @Override
    public boolean dropAllTables(String databaseName) throws SQLException {
        validateDatabaseName(databaseName);

        try (Connection connection = getDatabaseConnection(databaseName)) {
            // Get all table names
            List<String> tableNames = new ArrayList<>();
            String getTablesSql = "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE'";
            
            try (PreparedStatement pstmt = connection.prepareStatement(getTablesSql)) {
                pstmt.setString(1, databaseName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        tableNames.add(rs.getString("table_name"));
                    }
                }
            }

            if (tableNames.isEmpty()) {
                logger.info("No tables found in database '{}'", databaseName);
                return true;
            }

            // Disable foreign key checks to avoid dependency issues
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 0");
            }

            // Drop all tables
            try (Statement stmt = connection.createStatement()) {
                for (String tableName : tableNames) {
                    String dropSql = "DROP TABLE " + escapeIdentifier(tableName);
                    stmt.executeUpdate(dropSql);
                    logger.debug("Dropped table '{}'", tableName);
                }
            }

            // Re-enable foreign key checks
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 1");
            }

            logger.info("Successfully dropped {} tables from database '{}'", tableNames.size(), databaseName);
            return true;

        } catch (SQLException e) {
            logger.error("Failed to drop tables from database '{}'", databaseName, e);
            throw e;
        }
    }

    @Override
    public boolean createBackup(String databaseName, String backupFilePath) throws SQLException, IOException {
        validateDatabaseName(databaseName);
        validateFilePath(backupFilePath);

        try {
            // Use mysqldump command for better backup
            ProcessBuilder processBuilder = new ProcessBuilder(
                "mysqldump",
                "--host=localhost",
                "--port=3306",
                "--user=" + adminUsername,
                "--password=" + adminPassword,
                "--single-transaction",
                "--routines",
                "--triggers",
                "--add-drop-database",
                "--databases",
                databaseName
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Write output to file
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 FileWriter writer = new FileWriter(backupFilePath, StandardCharsets.UTF_8)) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line + System.lineSeparator());
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Database backup created successfully: {}", backupFilePath);
                return true;
            } else {
                logger.error("mysqldump process failed with exit code: {}", exitCode);
                return false;
            }

        } catch (IOException | InterruptedException e) {
            logger.error("Failed to create backup for database '{}'", databaseName, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("Backup creation failed", e);
        }
    }

    @Override
    public boolean restoreDatabase(String databaseName, String backupFilePath) throws SQLException, IOException {
        validateDatabaseName(databaseName);
        validateFilePath(backupFilePath);

        if (!Files.exists(Paths.get(backupFilePath))) {
            throw new IOException("Backup file does not exist: " + backupFilePath);
        }

        try {
            // Use mysql command to restore from backup
            ProcessBuilder processBuilder = new ProcessBuilder(
                "mysql",
                "--host=localhost",
                "--port=3306",
                "--user=" + adminUsername,
                "--password=" + adminPassword
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Write backup file content to mysql process
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                 BufferedReader fileReader = Files.newBufferedReader(Paths.get(backupFilePath))) {
                
                String line;
                while ((line = fileReader.readLine()) != null) {
                    writer.write(line + System.lineSeparator());
                }
            }

            // Read any output/errors
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("mysql output: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Database '{}' restored successfully from: {}", databaseName, backupFilePath);
                return true;
            } else {
                logger.error("mysql restore process failed with exit code: {}", exitCode);
                return false;
            }

        } catch (IOException | InterruptedException e) {
            logger.error("Failed to restore database '{}' from backup '{}'", databaseName, backupFilePath, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("Database restoration failed", e);
        }
    }

    @Override
    public boolean changeUserPassword(String databaseName, String username, String newPassword) throws SQLException {
        validateDatabaseName(databaseName);
        validateUsername(username);
        validatePassword(newPassword);

        try (Connection connection = getServerConnection()) {
            String alterUserSql = "ALTER USER ?@'%' IDENTIFIED BY ?";
            
            try (PreparedStatement pstmt = connection.prepareStatement(alterUserSql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, newPassword);
                pstmt.executeUpdate();
            }

            // Flush privileges
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("FLUSH PRIVILEGES");
            }

            logger.info("Password changed successfully for user '{}' on database '{}'", username, databaseName);
            return true;

        } catch (SQLException e) {
            logger.error("Failed to change password for user '{}' on database '{}'", username, databaseName, e);
            throw e;
        }
    }

    @Override
    public boolean testConnection() {
        try (Connection connection = getServerConnection()) {
            return connection.isValid(5); // 5 seconds timeout
        } catch (SQLException e) {
            logger.error("Database connection test failed", e);
            return false;
        }
    }

    // Validation methods
    private void validateDatabaseName(String databaseName) {
        if (databaseName == null || !VALID_DB_NAME_PATTERN.matcher(databaseName).matches()) {
            throw new IllegalArgumentException("Invalid database name. Must be 3-64 characters; allowed: letters, numbers, underscore, hyphen.");
        }
    }

    private void validateUsername(String username) {
        if (username == null || !VALID_USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Invalid username. Must be 3-64 characters; allowed: letters, numbers, underscore, hyphen.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || !VALID_PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("Invalid password. Must be 8-128 characters; hyphen is allowed.");
        }
    }

    private void validateFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
    }

    /**
     * Escapes MySQL identifiers (database names, table names, etc.)
     */
    private String escapeIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
