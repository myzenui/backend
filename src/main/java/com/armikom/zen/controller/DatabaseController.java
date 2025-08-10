package com.armikom.zen.controller;

import com.armikom.zen.dto.*;
import com.armikom.zen.service.DatabaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for database management operations
 */
@RestController
@RequestMapping("/api/database")
@Tag(name = "Database Management", description = "Database operations including creation, backup, restore, and user management")
public class DatabaseController {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseController.class);
    private final DatabaseService databaseService;

    public DatabaseController(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @PostMapping("/create")
    @Operation(summary = "Create a new database with admin user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Database created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Database creation failed")
    })
    public ResponseEntity<DatabaseOperationResponse> createDatabase(
            @Valid @RequestBody CreateDatabaseRequest request,
            BindingResult bindingResult) {
        
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().get(0).getDefaultMessage();
            logger.warn("Validation error for database creation: {}", errorMessage);
            return ResponseEntity.badRequest()
                    .body(DatabaseOperationResponse.error("Validation failed: " + errorMessage));
        }
        
        try {
            boolean success = databaseService.createDatabase(
                    request.getDatabaseName(), 
                    request.getUsername(), 
                    request.getPassword());
            
            if (success) {
                DatabaseOperationResponse response = DatabaseOperationResponse
                        .success("Database created successfully")
                        .withDatabaseName(request.getDatabaseName())
                        .withUsername(request.getUsername());
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(DatabaseOperationResponse.error("Failed to create database"));
            }
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid parameters for database creation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(DatabaseOperationResponse.error("Invalid parameters", e.getMessage()));
            
        } catch (Exception e) {
            logger.error("Failed to create database '{}' with user '{}'", 
                    request.getDatabaseName(), request.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(DatabaseOperationResponse.error("Database creation failed", e.getMessage()));
        }
    }

    @PostMapping("/trim-tables")
    @Operation(summary = "Truncate all tables in a database")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tables truncated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid database name"),
        @ApiResponse(responseCode = "500", description = "Table truncation failed")
    })
    public ResponseEntity<DatabaseOperationResponse> trimAllTables(
            @Valid @RequestBody TrimTablesRequest request,
            BindingResult bindingResult) {
        
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().get(0).getDefaultMessage();
            logger.warn("Validation error for table truncation: {}", errorMessage);
            return ResponseEntity.badRequest()
                    .body(DatabaseOperationResponse.error("Validation failed: " + errorMessage));
        }
        
        try {
            boolean success = databaseService.trimAllTables(request.getDatabaseName());
            
            if (success) {
                DatabaseOperationResponse response = DatabaseOperationResponse
                        .success("All tables truncated successfully")
                        .withDatabaseName(request.getDatabaseName());
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(DatabaseOperationResponse.error("Failed to truncate tables"));
            }
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid database name for table truncation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(DatabaseOperationResponse.error("Invalid parameters", e.getMessage()));
            
        } catch (Exception e) {
            logger.error("Failed to truncate tables in database '{}'", request.getDatabaseName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(DatabaseOperationResponse.error("Table truncation failed", e.getMessage()));
        }
    }

    @PostMapping("/drop-tables")
    @Operation(summary = "Drop all tables from a database")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tables dropped successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid database name"),
        @ApiResponse(responseCode = "500", description = "Table dropping failed")
    })
    public ResponseEntity<DatabaseOperationResponse> dropAllTables(
            @Valid @RequestBody DropTablesRequest request,
            BindingResult bindingResult) {
        
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().get(0).getDefaultMessage();
            logger.warn("Validation error for dropping tables: {}", errorMessage);
            return ResponseEntity.badRequest()
                    .body(DatabaseOperationResponse.error("Validation failed: " + errorMessage));
        }
        
        try {
            boolean success = databaseService.dropAllTables(request.getDatabaseName());
            
            if (success) {
                DatabaseOperationResponse response = DatabaseOperationResponse
                        .success("All tables dropped successfully")
                        .withDatabaseName(request.getDatabaseName());
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(DatabaseOperationResponse.error("Failed to drop tables"));
            }
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid database name for dropping tables: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(DatabaseOperationResponse.error("Invalid parameters", e.getMessage()));
            
        } catch (Exception e) {
            logger.error("Failed to drop tables from database '{}'", request.getDatabaseName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(DatabaseOperationResponse.error("Table dropping failed", e.getMessage()));
        }
    }

    @PostMapping("/backup")
    @Operation(summary = "Create a backup of a database")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Backup created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Backup creation failed")
    })
    public ResponseEntity<DatabaseOperationResponse> createBackup(
            @Valid @RequestBody BackupRequest request,
            BindingResult bindingResult) {
        
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().get(0).getDefaultMessage();
            logger.warn("Validation error for backup creation: {}", errorMessage);
            return ResponseEntity.badRequest()
                    .body(DatabaseOperationResponse.error("Validation failed: " + errorMessage));
        }
        
        try {
            boolean success = databaseService.createBackup(
                    request.getDatabaseName(), 
                    request.getBackupFilePath());
            
            if (success) {
                DatabaseOperationResponse response = DatabaseOperationResponse
                        .success("Backup created successfully")
                        .withDatabaseName(request.getDatabaseName())
                        .withFilePath(request.getBackupFilePath());
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(DatabaseOperationResponse.error("Failed to create backup"));
            }
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid parameters for backup creation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(DatabaseOperationResponse.error("Invalid parameters", e.getMessage()));
            
        } catch (Exception e) {
            logger.error("Failed to create backup for database '{}' to '{}'", 
                    request.getDatabaseName(), request.getBackupFilePath(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(DatabaseOperationResponse.error("Backup creation failed", e.getMessage()));
        }
    }

    @PostMapping("/restore")
    @Operation(summary = "Restore a database from backup")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Database restored successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Database restoration failed")
    })
    public ResponseEntity<DatabaseOperationResponse> restoreDatabase(
            @Valid @RequestBody RestoreRequest request,
            BindingResult bindingResult) {
        
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().get(0).getDefaultMessage();
            logger.warn("Validation error for database restoration: {}", errorMessage);
            return ResponseEntity.badRequest()
                    .body(DatabaseOperationResponse.error("Validation failed: " + errorMessage));
        }
        
        try {
            boolean success = databaseService.restoreDatabase(
                    request.getDatabaseName(), 
                    request.getBackupFilePath());
            
            if (success) {
                DatabaseOperationResponse response = DatabaseOperationResponse
                        .success("Database restored successfully")
                        .withDatabaseName(request.getDatabaseName())
                        .withFilePath(request.getBackupFilePath());
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(DatabaseOperationResponse.error("Failed to restore database"));
            }
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid parameters for database restoration: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(DatabaseOperationResponse.error("Invalid parameters", e.getMessage()));
            
        } catch (Exception e) {
            logger.error("Failed to restore database '{}' from '{}'", 
                    request.getDatabaseName(), request.getBackupFilePath(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(DatabaseOperationResponse.error("Database restoration failed", e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password for a database user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password changed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Password change failed")
    })
    public ResponseEntity<DatabaseOperationResponse> changeUserPassword(
            @Valid @RequestBody ChangePasswordRequest request,
            BindingResult bindingResult) {
        
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().get(0).getDefaultMessage();
            logger.warn("Validation error for password change: {}", errorMessage);
            return ResponseEntity.badRequest()
                    .body(DatabaseOperationResponse.error("Validation failed: " + errorMessage));
        }
        
        try {
            boolean success = databaseService.changeUserPassword(
                    request.getDatabaseName(), 
                    request.getUsername(), 
                    request.getNewPassword());
            
            if (success) {
                DatabaseOperationResponse response = DatabaseOperationResponse
                        .success("Password changed successfully")
                        .withDatabaseName(request.getDatabaseName())
                        .withUsername(request.getUsername());
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(DatabaseOperationResponse.error("Failed to change password"));
            }
            
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid parameters for password change: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(DatabaseOperationResponse.error("Invalid parameters", e.getMessage()));
            
        } catch (Exception e) {
            logger.error("Failed to change password for user '{}' on database '{}'", 
                    request.getUsername(), request.getDatabaseName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(DatabaseOperationResponse.error("Password change failed", e.getMessage()));
        }
    }

    @GetMapping("/test-connection")
    @Operation(summary = "Test database connection")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Connection status returned")
    })
    public ResponseEntity<ConnectionTestResponse> testConnection() {
        try {
            long startTime = System.currentTimeMillis();
            boolean connected = databaseService.testConnection();
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (connected) {
                ConnectionTestResponse response = ConnectionTestResponse
                        .success("Database connection successful")
                        .withTimeout(5000L)
                        .withResponseTime(responseTime);
                return ResponseEntity.ok(response);
            } else {
                ConnectionTestResponse response = ConnectionTestResponse
                        .error("Database connection failed")
                        .withTimeout(5000L)
                        .withResponseTime(responseTime);
                return ResponseEntity.ok(response);
            }
            
        } catch (Exception e) {
            logger.error("Error testing database connection", e);
            ConnectionTestResponse response = ConnectionTestResponse
                    .error("Connection test failed", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}
