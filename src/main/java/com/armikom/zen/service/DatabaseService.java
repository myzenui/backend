package com.armikom.zen.service;

import com.armikom.zen.enums.DatabaseEnvironment;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Database management service for handling database operations including
 * database creation, user management, backup, and restore operations.
 */
public interface DatabaseService {

    /**
     * Creates a new database with the given username and password as admin
     * @param environment The database environment (preview or production)
     * @param databaseName The name of the database to create
     * @param username The username for the database admin user
     * @param password The password for the database admin user
     * @return true if successful, false otherwise
     * @throws SQLException if database operation fails
     */
    boolean createDatabase(DatabaseEnvironment environment, String databaseName, String username, String password) throws SQLException;

    /**
     * Truncates all tables for a given database
     * @param environment The database environment (preview or production)
     * @param databaseName The name of the database to trim tables
     * @return true if successful, false otherwise
     * @throws SQLException if database operation fails
     */
    boolean trimAllTables(DatabaseEnvironment environment, String databaseName) throws SQLException;

    /**
     * Drops all tables from a given database (completely removes tables)
     * @param environment The database environment (preview or production)
     * @param databaseName The name of the database to drop all tables from
     * @return true if successful, false otherwise
     * @throws SQLException if database operation fails
     */
    boolean dropAllTables(DatabaseEnvironment environment, String databaseName) throws SQLException;

    /**
     * Creates a backup for the given database with drop and create statements
     * @param environment The database environment (preview or production)
     * @param databaseName The name of the database to backup
     * @param backupFilePath The file path where the backup will be saved
     * @return true if successful, false otherwise
     * @throws SQLException if database operation fails
     * @throws IOException if file operation fails
     */
    boolean createBackup(DatabaseEnvironment environment, String databaseName, String backupFilePath) throws SQLException, IOException;

    /**
     * Restores a database from the given backup file
     * @param environment The database environment (preview or production)
     * @param databaseName The name of the database to restore to
     * @param backupFilePath The file path of the backup to restore from
     * @return true if successful, false otherwise
     * @throws SQLException if database operation fails
     * @throws IOException if file operation fails
     */
    boolean restoreDatabase(DatabaseEnvironment environment, String databaseName, String backupFilePath) throws SQLException, IOException;

    /**
     * Changes the password of a user for a given database
     * @param environment The database environment (preview or production)
     * @param databaseName The name of the database
     * @param username The username whose password will be changed
     * @param newPassword The new password
     * @return true if successful, false otherwise
     * @throws SQLException if database operation fails
     */
    boolean changeUserPassword(DatabaseEnvironment environment, String databaseName, String username, String newPassword) throws SQLException;

    /**
     * Tests the connection to the database with the current configuration
     * @param environment The database environment (preview or production)
     * @return true if connection is successful, false otherwise
     */
    boolean testConnection(DatabaseEnvironment environment);
}
