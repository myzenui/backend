package com.armikom.zen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new database with admin user
 */
@Schema(description = "Request to create a new database with admin user")
public class CreateDatabaseRequest {

    @NotBlank(message = "Database name is required")
    @Size(min = 3, max = 64, message = "Database name must be between 3 and 64 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Database name can only contain letters, numbers, and underscores")
    @Schema(description = "Name of the database to create", example = "my_project_db")
    private String databaseName;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 64, message = "Username must be between 3 and 64 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    @Schema(description = "Username for the database admin", example = "dbadmin")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @Pattern(regexp = "^[a-zA-Z0-9@#$%^&+=!]+$", message = "Password can only contain letters, numbers, and special characters (@#$%^&+=!)")
    @Schema(description = "Password for the database admin", example = "SecurePass123!")
    private String password;

    // Default constructor
    public CreateDatabaseRequest() {}

    // Constructor with all fields
    public CreateDatabaseRequest(String databaseName, String username, String password) {
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
    }

    // Getters and setters
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "CreateDatabaseRequest{" +
                "databaseName='" + databaseName + '\'' +
                ", username='" + username + '\'' +
                ", password='[PROTECTED]'" +
                '}';
    }
}

