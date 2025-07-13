package com.armikom.zen.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import com.armikom.zen.service.DockerService;
import com.armikom.zen.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/test")
@Tag(name = "Test Controller", description = "Test API endpoints")
public class TestController {

    @Autowired
    private DockerService dockerService;
    
    @Autowired
    private ProjectService projectService;

    @GetMapping("/hello")
    @Operation(summary = "Hello World", description = "Returns a simple hello world message")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully returned hello world message")
    })
    public String helloWorld() {
        return "Hello dear Great World!";
    }

    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "Simple health check endpoint")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Service is healthy")
    })
    public String health() {
        return "Service is up and running!";
    }

    @PostMapping("/docker/push")
    @Operation(summary = "Push Docker Image", description = "Push a Docker image to registry")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Docker push operation completed"),
        @ApiResponse(responseCode = "400", description = "Invalid image tag provided"),
        @ApiResponse(responseCode = "500", description = "Docker operation failed")
    })
    public ResponseEntity<Map<String, Object>> pushDockerImage(
            @Parameter(description = "Docker image tag to push", example = "europe-west1-docker.pkg.dev/myzenui/image/zen-backend:latest")
            @RequestParam(defaultValue = "europe-west1-docker.pkg.dev/myzenui/image/zen-backend:latest") String imageTag) {
        
        Map<String, Object> response = new HashMap<>();
        
        boolean result = dockerService.pushDockerImage(imageTag);
        response.put("success", result);
        response.put("imageTag", imageTag);
        response.put("message", result ? "Docker image pushed successfully" : "Docker push failed");
        
        return result ? ResponseEntity.ok(response) : ResponseEntity.internalServerError().body(response);
    }

    @GetMapping("/docker/status")
    @Operation(summary = "Check Docker Status", description = "Check if Docker is available on the system")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Docker status retrieved successfully")
    })
    public ResponseEntity<Map<String, Object>> getDockerStatus() {
        Map<String, Object> response = new HashMap<>();
        boolean isDockerAvailable = dockerService.isDockerAvailable();
        
        response.put("dockerAvailable", isDockerAvailable);
        response.put("message", isDockerAvailable ? "Docker is available" : "Docker is not available");
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/docker/debug")
    @Operation(summary = "Debug Docker Configuration", description = "Show detailed Docker configuration and connection information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Docker debug information retrieved successfully")
    })
    public ResponseEntity<Map<String, Object>> getDockerDebugInfo() {
        Map<String, Object> response = new HashMap<>();
        
        // Environment information
        response.put("dockerHostEnv", System.getenv("DOCKER_HOST"));
        response.put("userHome", System.getProperty("user.home"));
        
        // Socket file checks
        String userHome = System.getProperty("user.home");
        response.put("colimaSocketExists", new java.io.File(userHome + "/.colima/default/docker.sock").exists());
        response.put("standardSocketExists", new java.io.File("/var/run/docker.sock").exists());
        response.put("dockerDesktopSocketExists", new java.io.File(userHome + "/.docker/run/docker.sock").exists());
        
        // Docker service status
        boolean isDockerAvailable = dockerService.isDockerAvailable();
        response.put("dockerAvailable", isDockerAvailable);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/project/create-db-account")
    @Operation(summary = "Create Database Account", description = "Create a new database user and database for a project")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Database account created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input parameters"),
        @ApiResponse(responseCode = "500", description = "Database operation failed")
    })
    public ResponseEntity<Map<String, Object>> createDatabaseAccount(
            @Parameter(description = "Username for the new database user", example = "project_user")
            @RequestParam String userName,
            @Parameter(description = "Password for the new database user", example = "StrongPassword123!")
            @RequestParam String password,
            @Parameter(description = "Project name (will be used as database name)", example = "myproject")
            @RequestParam String projectName) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean result = projectService.createDbAccount(userName, password, projectName);
            
            response.put("success", result);
            response.put("userName", userName);
            response.put("projectName", projectName);
            response.put("message", result ? 
                "Database account created successfully" : 
                "Failed to create database account");
            
            return result ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/project/test-connection")
    @Operation(summary = "Test Database Connection", description = "Test the connection to Microsoft SQL Server")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Database connection test completed")
    })
    public ResponseEntity<Map<String, Object>> testDatabaseConnection() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean connectionWorking = projectService.testConnection();
            
            response.put("connectionWorking", connectionWorking);
            response.put("message", connectionWorking ? 
                "Database connection successful" : 
                "Database connection failed");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("connectionWorking", false);
            response.put("message", "Connection test failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
} 