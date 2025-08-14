package com.armikom.zen.dto;

import com.armikom.zen.enums.DatabaseEnvironment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for dropping all tables from a database
 */
@Schema(description = "Request to drop all tables from a database (completely removes tables)")
public class DropTablesRequest {

    @NotNull(message = "Database environment is required")
    @Schema(description = "Database environment (preview or production)", example = "PREVIEW")
    private DatabaseEnvironment environment;

    @NotBlank(message = "Database name is required")
    @Size(min = 3, max = 64, message = "Database name must be between 3 and 64 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Database name can only contain letters, numbers, underscores, and hyphens")
    @Schema(description = "Name of the database to drop all tables from", example = "my_project_db")
    private String databaseName;

    // Default constructor
    public DropTablesRequest() {}

    // Constructor
    public DropTablesRequest(DatabaseEnvironment environment, String databaseName) {
        this.environment = environment;
        this.databaseName = databaseName;
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

    @Override
    public String toString() {
        return "DropTablesRequest{" +
                "environment=" + environment +
                ", databaseName='" + databaseName + '\'' +
                '}';
    }
}
