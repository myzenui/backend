package com.armikom.zen.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import com.armikom.zen.service.DockerService;
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
} 