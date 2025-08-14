package com.armikom.zen.dto;

import com.armikom.zen.enums.DatabaseEnvironment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a database backup
 */
@Schema(description = "Request to create a database backup")
public class BackupRequest {

    @NotNull(message = "Database environment is required")
    @Schema(description = "Database environment (preview or production)", example = "PREVIEW")
    private DatabaseEnvironment environment;

    @NotBlank(message = "Database name is required")
    @Size(min = 3, max = 64, message = "Database name must be between 3 and 64 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Database name can only contain letters, numbers, underscores, and hyphens")
    @Schema(description = "Name of the database to backup", example = "my_project_db")
    private String databaseName;

    @NotBlank(message = "Backup file path is required")
    @Schema(description = "File path where backup will be saved", example = "/tmp/backup_my_project_db.sql")
    private String backupFilePath;

    // Default constructor
    public BackupRequest() {}

    // Constructor
    public BackupRequest(DatabaseEnvironment environment, String databaseName, String backupFilePath) {
        this.environment = environment;
        this.databaseName = databaseName;
        this.backupFilePath = backupFilePath;
    }

    // Getters and setters
    public DatabaseEnvironment getEnvironment() {
        return environment;
    }

    public void setEnvironment(DatabaseEnvironment environment) {
        this.environment = environment;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getBackupFilePath() {
        return backupFilePath;
    }

    public void setBackupFilePath(String backupFilePath) {
        this.backupFilePath = backupFilePath;
    }

    @Override
    public String toString() {
        return "BackupRequest{" +
                "environment=" + environment +
                ", databaseName='" + databaseName + '\'' +
                ", backupFilePath='" + backupFilePath + '\'' +
                '}';
    }
}

