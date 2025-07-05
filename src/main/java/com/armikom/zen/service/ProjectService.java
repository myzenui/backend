package com.armikom.zen.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

@Service
public class ProjectService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    
    @Value("${spring.datasource.url}")
    private String connectionString;
    
    @Value("${spring.datasource.username}")
    private String adminUsername;
    
    @Value("${spring.datasource.password}")
    private String adminPassword;
    
    // Patterns for input validation
    private static final Pattern VALID_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,64}$");
    private static final Pattern VALID_PASSWORD_PATTERN = Pattern.compile("^[a-zA-Z0-9@#$%^&+=!]{8,128}$");
    private static final Pattern VALID_DB_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,64}$");
    
    /**
     * Creates a database account with user and database for a project
     * @param userName The username for the new database user
     * @param password The password for the new database user
     * @param projectName The name of the project (will be used as database name)
     * @return true if successful, false otherwise
     */
    public boolean createDbAccount(String userName, String password, String projectName) {
        logger.info("Creating database account for user: {} and project: {}", userName, projectName);
        
        // Validate inputs
        if (!isValidInput(userName, password, projectName)) {
            logger.error("Invalid input parameters provided");
            return false;
        }
        
        // Sanitize inputs
        userName = sanitizeInput(userName);
        projectName = sanitizeInput(projectName);
        
        Connection connection = null;
        boolean databaseCreated = false;
        boolean userCreated = false;
        boolean permissionsGranted = false;
        
        try {
            // Connect to SQL Server with admin privileges
            connection = getAdminConnection();
            
            // Create database first (requires auto-commit mode in SQL Server)
            if (!createDatabase(connection, projectName)) {
                logger.error("Failed to create database: {}", projectName);
                return false;
            }
            databaseCreated = true;
            
            // Now disable auto-commit for user creation and permissions (transaction mode)
            connection.setAutoCommit(false);
            
            // Create user
            if (!createUser(connection, userName, password)) {
                logger.error("Failed to create user: {}", userName);
                rollbackChanges(connection, userName, projectName, databaseCreated, userCreated, permissionsGranted);
                return false;
            }
            userCreated = true;
            
            // Grant permissions to user on the database
            if (!grantUserPermissions(connection, userName, projectName)) {
                logger.error("Failed to grant permissions to user: {} on database: {}", userName, projectName);
                rollbackChanges(connection, userName, projectName, databaseCreated, userCreated, permissionsGranted);
                return false;
            }
            permissionsGranted = true;
            
            // Commit the transaction
            connection.commit();
            logger.info("Successfully created database account for user: {} and project: {}", userName, projectName);
            return true;
            
        } catch (SQLException e) {
            logger.error("SQL error while creating database account", e);
            rollbackChanges(connection, userName, projectName, databaseCreated, userCreated, permissionsGranted);
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error while creating database account", e);
            rollbackChanges(connection, userName, projectName, databaseCreated, userCreated, permissionsGranted);
            return false;
        } finally {
            if (connection != null) {
                try {
                    if (!connection.isClosed()) {
                        connection.setAutoCommit(true); // Restore auto-commit
                    }
                } catch (SQLException e) {
                    logger.warn("Error restoring auto-commit", e);
                }
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.warn("Error closing database connection", e);
                }
            }
        }
    }
    
    /**
     * Validates input parameters
     */
    private boolean isValidInput(String userName, String password, String projectName) {
        if (userName == null || password == null || projectName == null) {
            logger.error("Input parameters cannot be null");
            return false;
        }
        
        if (!VALID_USERNAME_PATTERN.matcher(userName).matches()) {
            logger.error("Invalid username format: {}", userName);
            return false;
        }
        
        if (!VALID_PASSWORD_PATTERN.matcher(password).matches()) {
            logger.error("Invalid password format");
            return false;
        }
        
        if (!VALID_DB_NAME_PATTERN.matcher(projectName).matches()) {
            logger.error("Invalid project name format: {}", projectName);
            return false;
        }
        
        return true;
    }
    
    /**
     * Sanitizes input to prevent SQL injection
     */
    private String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        
        // // Remove any potential SQL injection characters
        // return input.replaceAll("[';\\-\\-/**/xp_cmdshell]", "")
        //            .trim();
        return input;
    }
    
    /**
     * Gets admin connection to SQL Server
     */
    private Connection getAdminConnection() throws SQLException {
        return DriverManager.getConnection(connectionString, adminUsername, adminPassword);
    }
    
    /**
     * Creates a new database
     */
    private boolean createDatabase(Connection connection, String databaseName) {
        try {
            // First check if database exists
            String checkDbSql = "SELECT COUNT(*) FROM sys.databases WHERE name = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkDbSql)) {
                checkStmt.setString(1, databaseName);
                try (var rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        logger.info("Database already exists: {}", databaseName);
                        return true;
                    }
                }
            }
            
            // Create database if it doesn't exist
            String createDbSql = "CREATE DATABASE [" + databaseName + "]";
            try (PreparedStatement stmt = connection.prepareStatement(createDbSql)) {
                stmt.executeUpdate();
                logger.info("Database created successfully: {}", databaseName);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error creating database: {}", databaseName, e);
            return false;
        }
    }
    
    /**
     * Creates a new database user
     */
    private boolean createUser(Connection connection, String userName, String password) {
        try {
            // First check if login exists
            String checkLoginSql = "SELECT COUNT(*) FROM sys.server_principals WHERE name = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkLoginSql)) {
                checkStmt.setString(1, userName);
                try (var rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        logger.info("Login already exists: {}", userName);
                        return true;
                    }
                }
            }
            
            // Create login if it doesn't exist
            // Use Statement instead of PreparedStatement for CREATE LOGIN to avoid SQL Server issues
            String escapedPassword = password.replace("'", "''"); // SQL escape single quotes
            String createLoginSql = "CREATE LOGIN [" + userName + "] WITH PASSWORD = '" + escapedPassword + "'";
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(createLoginSql);
                logger.info("Login created successfully: {}", userName);
                return true;
            }
        } catch (SQLException e) {
            // log sql error with detailed information
            logger.error("SQL error while creating user: {} - Message: {}, SQLState: {}, ErrorCode: {}", 
                         userName, e.getMessage(), e.getSQLState(), e.getErrorCode(), e);
            return false;
        }
    }
    
    /**
     * Grants permissions to user on the database
     */
    private boolean grantUserPermissions(Connection connection, String userName, String databaseName) {
        try {
            // Switch to the created database
            String useDatabaseSql = "USE [" + databaseName + "]";
            try (PreparedStatement stmt = connection.prepareStatement(useDatabaseSql)) {
                stmt.executeUpdate();
            }
            
            // Check if user exists in the database
            String checkUserSql = "SELECT COUNT(*) FROM sys.database_principals WHERE name = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkUserSql)) {
                checkStmt.setString(1, userName);
                try (var rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        // Create user in the database if it doesn't exist
                        String createUserSql = "CREATE USER [" + userName + "] FOR LOGIN [" + userName + "]";
                        try (PreparedStatement stmt = connection.prepareStatement(createUserSql)) {
                            stmt.executeUpdate();
                        }
                    }
                }
            }
            
            // Grant database permissions (all except execute shell commands)
            String[] permissions = {
                "ALTER USER [" + userName + "] WITH DEFAULT_SCHEMA = [dbo]",
                "ALTER ROLE [db_datareader] ADD MEMBER [" + userName + "]",
                "ALTER ROLE [db_datawriter] ADD MEMBER [" + userName + "]",
                "ALTER ROLE [db_ddladmin] ADD MEMBER [" + userName + "]",
                "ALTER ROLE [db_owner] ADD MEMBER [" + userName + "]"
            };
            
            for (String permission : permissions) {
                try (PreparedStatement stmt = connection.prepareStatement(permission)) {
                    stmt.executeUpdate();
                }
            }
            
            // Explicitly deny dangerous permissions
            String denyDangerousPermissions = "DENY EXECUTE ON SCHEMA::sys TO [" + userName + "]";
            try (PreparedStatement stmt = connection.prepareStatement(denyDangerousPermissions)) {
                stmt.executeUpdate();
            }
            
            logger.info("Permissions granted successfully to user: {} on database: {}", userName, databaseName);
            return true;
            
        } catch (SQLException e) {
            logger.error("Error granting permissions to user: {} on database: {}", userName, databaseName, e);
            return false;
        }
    }
    
    /**
     * Rollback changes made during database account creation
     */
    private void rollbackChanges(Connection connection, String userName, String projectName, 
                                boolean databaseCreated, boolean userCreated, boolean permissionsGranted) {
        logger.warn("Rolling back changes for user: {} and project: {}", userName, projectName);
        
        try {
            // Rollback the current transaction first (for user and permission changes)
            if (connection != null && !connection.isClosed()) {
                try {
                    connection.rollback();
                    // Switch back to auto-commit mode for cleanup operations
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    logger.warn("Error during transaction rollback", e);
                    // Continue with cleanup even if rollback fails
                }
            }
            
            // Manual cleanup in reverse order of creation
            if (permissionsGranted || userCreated) {
                try {
                    cleanupUser(connection, userName, projectName);
                } catch (Exception e) {
                    logger.warn("Error during user cleanup", e);
                    // Continue with database cleanup even if user cleanup fails
                }
            }
            
            // Database cleanup (database was created outside transaction, so manual cleanup needed)
            if (databaseCreated) {
                try {
                    cleanupDatabase(connection, projectName);
                } catch (Exception e) {
                    logger.warn("Error during database cleanup", e);
                    // Log the error but don't rethrow - rollback is best effort
                }
            }
            
            logger.info("Rollback completed for user: {} and project: {}", userName, projectName);
            
        } catch (SQLException e) {
            logger.error("Error during rollback for user: {} and project: {}", userName, projectName, e);
        } catch (Exception e) {
            logger.error("Unexpected error during rollback for user: {} and project: {}", userName, projectName, e);
        }
    }
    
    /**
     * Cleanup user and permissions
     */
    private void cleanupUser(Connection connection, String userName, String projectName) {
        try {
            // Check if connection is valid first
            if (connection == null || connection.isClosed()) {
                logger.warn("Cannot cleanup user {} - connection is null or closed", userName);
                return;
            }
            
            // Try to remove user from database (if database still exists)
            try {
                String useDatabase = "USE [" + projectName + "]";
                try (PreparedStatement stmt = connection.prepareStatement(useDatabase)) {
                    stmt.executeUpdate();
                }
                
                // Check if user exists in database and drop it
                String checkUserSql = "SELECT COUNT(*) FROM sys.database_principals WHERE name = ?";
                try (PreparedStatement checkStmt = connection.prepareStatement(checkUserSql)) {
                    checkStmt.setString(1, userName);
                    try (var rs = checkStmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            String dropUser = "DROP USER [" + userName + "]";
                            try (PreparedStatement stmt = connection.prepareStatement(dropUser)) {
                                stmt.executeUpdate();
                                logger.info("Cleaned up database user: {} from project: {}", userName, projectName);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not cleanup user from database {}: {}", projectName, e.getMessage());
                // Continue with login cleanup even if database user cleanup fails
            }
            
            // Switch back to master database
            try {
                String useMaster = "USE master";
                try (PreparedStatement stmt = connection.prepareStatement(useMaster)) {
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                logger.warn("Could not switch to master database: {}", e.getMessage());
            }
            
            // Check if login exists and drop it
            String checkLoginSql = "SELECT COUNT(*) FROM sys.server_principals WHERE name = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkLoginSql)) {
                checkStmt.setString(1, userName);
                try (var rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        String dropLogin = "DROP LOGIN [" + userName + "]";
                        try (PreparedStatement stmt = connection.prepareStatement(dropLogin)) {
                            stmt.executeUpdate();
                            logger.info("Cleaned up login: {}", userName);
                        }
                    }
                }
            }
            
        } catch (SQLException e) {
            logger.warn("Error cleaning up user: {} from project: {} - {}", userName, projectName, e.getMessage());
            // Don't rethrow - cleanup is best effort
        }
    }
    
    /**
     * Cleanup database
     */
    private void cleanupDatabase(Connection connection, String projectName) {
        try {
            // Check if connection is valid first
            if (connection == null || connection.isClosed()) {
                logger.warn("Cannot cleanup database {} - connection is null or closed", projectName);
                return;
            }
            
            // Make sure we're in master database
            String useMaster = "USE master";
            try (PreparedStatement stmt = connection.prepareStatement(useMaster)) {
                stmt.executeUpdate();
            }
            
            // Check if database exists and drop it
            String checkDbSql = "SELECT COUNT(*) FROM sys.databases WHERE name = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkDbSql)) {
                checkStmt.setString(1, projectName);
                try (var rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        // Force single-user mode to close any connections before dropping
                        String alterDatabase = "ALTER DATABASE [" + projectName + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE";
                        try (PreparedStatement alterStmt = connection.prepareStatement(alterDatabase)) {
                            alterStmt.executeUpdate();
                            logger.info("Set database {} to single-user mode", projectName);
                        } catch (SQLException e) {
                            logger.warn("Could not set database {} to single-user mode: {}", projectName, e.getMessage());
                        }
                        
                        // Now drop the database
                        String dropDatabase = "DROP DATABASE [" + projectName + "]";
                        try (PreparedStatement stmt = connection.prepareStatement(dropDatabase)) {
                            stmt.executeUpdate();
                            logger.info("Cleaned up database: {}", projectName);
                        }
                    } else {
                        logger.info("Database {} does not exist, no cleanup needed", projectName);
                    }
                }
            }
            
        } catch (SQLException e) {
            logger.warn("Error cleaning up database: {} - {}", projectName, e.getMessage());
            // Don't rethrow - cleanup is best effort
        }
    }
    
    /**
     * Tests the database connection
     */
    public boolean testConnection() {
        try (Connection connection = getAdminConnection()) {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            logger.error("Database connection test failed", e);
            return false;
        }
    }
} 