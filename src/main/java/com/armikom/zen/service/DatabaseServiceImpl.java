package com.armikom.zen.service;

import com.armikom.zen.enums.DatabaseEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Microsoft SQL Server implementation of DatabaseService for handling database operations
 */
@Service
public class DatabaseServiceImpl implements DatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseServiceImpl.class);

    // Patterns for input validation (allow hyphen to support project ids like "tour-buddy")
    private static final Pattern VALID_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,64}$");
    private static final Pattern VALID_PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9@#$%^&+=!-]{8,128}$");
    private static final Pattern VALID_DB_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,64}$");

    // Preview Database Configuration
    @Value("${database.preview.url}")
    private String previewConnectionString;

    @Value("${database.preview.username}")
    private String previewAdminUsername;

    @Value("${database.preview.password}")
    private String previewAdminPassword;

    @Value("${database.preview.driver-class-name}")
    private String previewDriverClassName;

    // Production Database Configuration
    @Value("${database.production.url}")
    private String productionConnectionString;

    @Value("${database.production.username}")
    private String productionAdminUsername;

    @Value("${database.production.password}")
    private String productionAdminPassword;

    @Value("${database.production.driver-class-name}")
    private String productionDriverClassName;

    /**
     * Gets database configuration for the specified environment
     */
    private DatabaseConfig getDatabaseConfig(DatabaseEnvironment environment) {
        switch (environment) {
            case PREVIEW:
                return new DatabaseConfig(previewConnectionString, previewAdminUsername, 
                                        previewAdminPassword, previewDriverClassName);
            case PRODUCTION:
                return new DatabaseConfig(productionConnectionString, productionAdminUsername, 
                                        productionAdminPassword, productionDriverClassName);
            default:
                throw new IllegalArgumentException("Unsupported database environment: " + environment);
        }
    }

    /**
     * Gets a connection to SQL Server (without specifying a database)
     */
    private Connection getServerConnection(DatabaseEnvironment environment) throws SQLException {
        DatabaseConfig config = getDatabaseConfig(environment);
        String baseUrl = removeDatabaseName(config.getUrl());
        return DriverManager.getConnection(baseUrl, config.getUsername(), config.getPassword());
    }

    /**
     * Gets a connection to a specific database
     */
    private Connection getDatabaseConnection(DatabaseEnvironment environment, String databaseName) throws SQLException {
        validateDatabaseName(databaseName);
        DatabaseConfig config = getDatabaseConfig(environment);
        String baseUrl = removeDatabaseName(config.getUrl());
        String dbUrl = baseUrl + ";databaseName=" + databaseName;
        return DriverManager.getConnection(dbUrl, config.getUsername(), config.getPassword());
    }

    /**
     * Inner class to hold database configuration
     */
    private static class DatabaseConfig {
        private final String url;
        private final String username;
        private final String password;
        private final String driverClassName;

        public DatabaseConfig(String url, String username, String password, String driverClassName) {
            this.url = url;
            this.username = username;
            this.password = password;
            this.driverClassName = driverClassName;
        }

        public String getUrl() { return url; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getDriverClassName() { return driverClassName; }
    }

    /**
     * Removes the databaseName parameter from a JDBC URL while preserving other parameters
     */
    private String removeDatabaseName(String url) {
        int idx = url.toLowerCase().indexOf(";databasename=");
        if (idx == -1) {
            return url;
        }
        int endIdx = url.indexOf(';', idx + 1);
        if (endIdx == -1) {
            return url.substring(0, idx);
        }
        return url.substring(0, idx) + url.substring(endIdx);
    }

    @Override
    public boolean createDatabase(DatabaseEnvironment environment, String databaseName, String username, String password) throws SQLException {
        validateDatabaseName(databaseName);
        validateUsername(username);
        validatePassword(password);

        try (Connection connection = getServerConnection(environment)) {
            // Create database if it doesn't exist
            String createDbSql = "IF DB_ID('" + databaseName + "') IS NULL CREATE DATABASE [" + databaseName + "]";
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(createDbSql);
                logger.info("Database '{}' created successfully", databaseName);
            }

            // Create login for the user if it doesn't exist
            String createLoginSql = "IF NOT EXISTS (SELECT * FROM sys.sql_logins WHERE name = ?) CREATE LOGIN [" + username + "] WITH PASSWORD = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(createLoginSql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, password);
                pstmt.executeUpdate();
                logger.info("Login '{}' created successfully", username);
            }
        }

        // Create database user and grant privileges
        try (Connection dbConn = getDatabaseConnection(environment, databaseName)) {
            String createUserSql = "IF NOT EXISTS (SELECT * FROM sys.database_principals WHERE name = '" + username + "') CREATE USER [" + username + "] FOR LOGIN [" + username + "]";
            try (Statement stmt = dbConn.createStatement()) {
                stmt.executeUpdate(createUserSql);
                stmt.executeUpdate("ALTER ROLE db_owner ADD MEMBER [" + username + "]");
                logger.info("User '{}' created and granted db_owner on database '{}'", username, databaseName);
            }

            return true;
        } catch (SQLException e) {
            logger.error("Failed to create database '{}' with user '{}'", databaseName, username, e);
            throw e;
        }
    }

    @Override
    public boolean trimAllTables(DatabaseEnvironment environment, String databaseName) throws SQLException {
        validateDatabaseName(databaseName);

        try (Connection connection = getDatabaseConnection(environment, databaseName)) {
            // Get all table names with their schemas
            List<String[]> tableNames = new ArrayList<>();
            String getTablesSql = "SELECT TABLE_SCHEMA, TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE'";

            try (PreparedStatement pstmt = connection.prepareStatement(getTablesSql);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    tableNames.add(new String[]{rs.getString("TABLE_SCHEMA"), rs.getString("TABLE_NAME")});
                }
            }

            if (tableNames.isEmpty()) {
                logger.info("No tables found in database '{}'", databaseName);
                return true;
            }

            try (Statement stmt = connection.createStatement()) {
                // Disable constraints
                stmt.execute("EXEC sp_msforeachtable 'ALTER TABLE ? NOCHECK CONSTRAINT ALL'");

                // Delete data from all tables
                for (String[] tbl : tableNames) {
                    String deleteSql = "DELETE FROM " + escapeIdentifier(tbl[0]) + "." + escapeIdentifier(tbl[1]);
                    stmt.executeUpdate(deleteSql);
                    logger.debug("Cleared table '{}.{}'", tbl[0], tbl[1]);
                }

                // Re-enable constraints
                stmt.execute("EXEC sp_msforeachtable 'ALTER TABLE ? WITH CHECK CHECK CONSTRAINT ALL'");
            }

            logger.info("Successfully cleared {} tables in database '{}'", tableNames.size(), databaseName);
            return true;

        } catch (SQLException e) {
            logger.error("Failed to clear tables in database '{}'", databaseName, e);
            throw e;
        }
    }

    @Override
    public boolean dropAllTables(DatabaseEnvironment environment, String databaseName) throws SQLException {
        validateDatabaseName(databaseName);

        try (Connection connection = getDatabaseConnection(environment, databaseName)) {
            // Get all table names with their schemas
            List<String[]> tableNames = new ArrayList<>();
            String getTablesSql = "SELECT TABLE_SCHEMA, TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE'";

            try (PreparedStatement pstmt = connection.prepareStatement(getTablesSql);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    tableNames.add(new String[]{rs.getString("TABLE_SCHEMA"), rs.getString("TABLE_NAME")});
                }
            }

            if (tableNames.isEmpty()) {
                logger.info("No tables found in database '{}'", databaseName);
                return true;
            }

            try (Statement stmt = connection.createStatement()) {
                // Disable constraints
                stmt.execute("EXEC sp_msforeachtable 'ALTER TABLE ? NOCHECK CONSTRAINT ALL'");

                // Drop all tables
                for (String[] tbl : tableNames) {
                    String dropSql = "DROP TABLE " + escapeIdentifier(tbl[0]) + "." + escapeIdentifier(tbl[1]);
                    stmt.executeUpdate(dropSql);
                    logger.debug("Dropped table '{}.{}'", tbl[0], tbl[1]);
                }

                // Re-enable constraints
                stmt.execute("EXEC sp_msforeachtable 'ALTER TABLE ? CHECK CONSTRAINT ALL'");
            }

            logger.info("Successfully dropped {} tables from database '{}'", tableNames.size(), databaseName);
            return true;

        } catch (SQLException e) {
            logger.error("Failed to drop tables from database '{}'", databaseName, e);
            throw e;
        }
    }

    @Override
    public boolean createBackup(DatabaseEnvironment environment, String databaseName, String backupFilePath) throws SQLException, IOException {
        validateDatabaseName(databaseName);
        validateFilePath(backupFilePath);

        try {
            DatabaseConfig config = getDatabaseConfig(environment);
            String backupCommand = String.format(
                "BACKUP DATABASE [%s] TO DISK='%s' WITH INIT",
                databaseName, backupFilePath.replace("'", "''"));

            ProcessBuilder processBuilder = new ProcessBuilder(
                "sqlcmd",
                "-S", "localhost",
                "-U", config.getUsername(),
                "-P", config.getPassword(),
                "-Q", backupCommand
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Database backup created successfully: {}", backupFilePath);
                return true;
            } else {
                logger.error("sqlcmd backup process failed with exit code: {}", exitCode);
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
    public boolean restoreDatabase(DatabaseEnvironment environment, String databaseName, String backupFilePath) throws SQLException, IOException {
        validateDatabaseName(databaseName);
        validateFilePath(backupFilePath);

        if (!Files.exists(Paths.get(backupFilePath))) {
            throw new IOException("Backup file does not exist: " + backupFilePath);
        }

        try {
            DatabaseConfig config = getDatabaseConfig(environment);
            String restoreCommand = String.format(
                "RESTORE DATABASE [%s] FROM DISK='%s' WITH REPLACE",
                databaseName, backupFilePath.replace("'", "''"));

            ProcessBuilder processBuilder = new ProcessBuilder(
                "sqlcmd",
                "-S", "localhost",
                "-U", config.getUsername(),
                "-P", config.getPassword(),
                "-Q", restoreCommand
            );

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Database '{}' restored successfully from: {}", databaseName, backupFilePath);
                return true;
            } else {
                logger.error("sqlcmd restore process failed with exit code: {}", exitCode);
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
    public boolean changeUserPassword(DatabaseEnvironment environment, String databaseName, String username, String newPassword) throws SQLException {
        validateDatabaseName(databaseName);
        validateUsername(username);
        validatePassword(newPassword);

        try (Connection connection = getServerConnection(environment)) {
            String alterLoginSql = "ALTER LOGIN [" + username + "] WITH PASSWORD = ?";

            try (PreparedStatement pstmt = connection.prepareStatement(alterLoginSql)) {
                pstmt.setString(1, newPassword);
                pstmt.executeUpdate();
            }

            logger.info("Password changed successfully for user '{}' on database '{}'", username, databaseName);
            return true;

        } catch (SQLException e) {
            logger.error("Failed to change password for user '{}' on database '{}'", username, databaseName, e);
            throw e;
        }
    }

    @Override
    public boolean testConnection(DatabaseEnvironment environment) {
        try (Connection connection = getServerConnection(environment)) {
            return connection.isValid(5); // 5 seconds timeout
        } catch (SQLException e) {
            logger.error("Database connection test failed for environment: {}", environment, e);
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
     * Escapes SQL Server identifiers (database names, table names, etc.)
     */
    private String escapeIdentifier(String identifier) {
        String sanitized = identifier.replace("]", "]]" );
        return "[" + sanitized + "]";
    }
}
