package com.armikom.zen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for changing a database user's password
 */
@Schema(description = "Request to change a database user's password")
public class ChangePasswordRequest {

    @NotBlank(message = "Database name is required")
    @Size(min = 3, max = 64, message = "Database name must be between 3 and 64 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Database name can only contain letters, numbers, and underscores")
    @Schema(description = "Name of the database", example = "my_project_db")
    private String databaseName;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 64, message = "Username must be between 3 and 64 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    @Schema(description = "Username whose password will be changed", example = "dbadmin")
    private String username;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @Pattern(regexp = "^[a-zA-Z0-9@#$%^&+=!]+$", message = "Password can only contain letters, numbers, and special characters (@#$%^&+=!)")
    @Schema(description = "New password for the user", example = "NewSecurePass456!")
    private String newPassword;

    // Default constructor
    public ChangePasswordRequest() {}

    // Constructor
    public ChangePasswordRequest(String databaseName, String username, String newPassword) {
        this.databaseName = databaseName;
        this.username = username;
        this.newPassword = newPassword;
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

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    @Override
    public String toString() {
        return "ChangePasswordRequest{" +
                "databaseName='" + databaseName + '\'' +
                ", username='" + username + '\'' +
                ", newPassword='[PROTECTED]'" +
                '}';
    }
}

