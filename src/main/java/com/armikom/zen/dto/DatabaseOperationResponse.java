package com.armikom.zen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Generic response DTO for database operations
 */
@Schema(description = "Response for database operations")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DatabaseOperationResponse {

    @Schema(description = "Whether the operation was successful", example = "true")
    private boolean success;

    @Schema(description = "Response message", example = "Database created successfully")
    private String message;

    @Schema(description = "Name of the database involved in the operation", example = "my_project_db")
    private String databaseName;

    @Schema(description = "Username involved in the operation", example = "dbadmin")
    private String username;

    @Schema(description = "File path for backup/restore operations", example = "/tmp/backup_my_project_db.sql")
    private String filePath;

    @Schema(description = "Error details if operation failed")
    private String errorDetails;

    @Schema(description = "Timestamp of the operation", example = "2024-01-15T10:30:00Z")
    private String timestamp;

    // Default constructor
    public DatabaseOperationResponse() {
        this.timestamp = java.time.Instant.now().toString();
    }

    // Constructor for success responses
    public DatabaseOperationResponse(boolean success, String message) {
        this();
        this.success = success;
        this.message = message;
    }

    // Constructor with database name
    public DatabaseOperationResponse(boolean success, String message, String databaseName) {
        this(success, message);
        this.databaseName = databaseName;
    }

    // Constructor with database name and username
    public DatabaseOperationResponse(boolean success, String message, String databaseName, String username) {
        this(success, message, databaseName);
        this.username = username;
    }

    // Static factory methods for common responses
    public static DatabaseOperationResponse success(String message) {
        return new DatabaseOperationResponse(true, message);
    }

    public static DatabaseOperationResponse success(String message, String databaseName) {
        return new DatabaseOperationResponse(true, message, databaseName);
    }

    public static DatabaseOperationResponse success(String message, String databaseName, String username) {
        return new DatabaseOperationResponse(true, message, databaseName, username);
    }

    public static DatabaseOperationResponse error(String message) {
        return new DatabaseOperationResponse(false, message);
    }

    public static DatabaseOperationResponse error(String message, String errorDetails) {
        DatabaseOperationResponse response = new DatabaseOperationResponse(false, message);
        response.setErrorDetails(errorDetails);
        return response;
    }

    // Builder-style methods for chaining
    public DatabaseOperationResponse withDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    public DatabaseOperationResponse withUsername(String username) {
        this.username = username;
        return this;
    }

    public DatabaseOperationResponse withFilePath(String filePath) {
        this.filePath = filePath;
        return this;
    }

    public DatabaseOperationResponse withErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
        return this;
    }

    // Getters and setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "DatabaseOperationResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", databaseName='" + databaseName + '\'' +
                ", username='" + username + '\'' +
                ", filePath='" + filePath + '\'' +
                ", errorDetails='" + errorDetails + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}

