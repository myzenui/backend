package com.armikom.zen.controller;

import com.armikom.zen.dto.ProjectRequest;
import com.armikom.zen.dto.ProjectResponse;
import com.armikom.zen.model.Project;
import com.armikom.zen.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Project Controller", description = "Project management API endpoints")
public class ProjectController {
    
    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);
    
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }
    
    @PostMapping("/retrieve")
    @Operation(
        summary = "Retrieve Project", 
        description = "Retrieves a project from Firebase Firestore using the provided project ID and auth token. The document content will be logged to the application logs.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Project retrieved and logged successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Invalid or missing authorization token"),
        @ApiResponse(responseCode = "404", description = "Project not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ProjectResponse> retrieveProject(
            @Parameter(description = "Authorization token", required = true, example = "Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6...")
            @RequestHeader("Authorization") String authToken,
            @Parameter(description = "Project request containing project ID")
            @Valid @RequestBody ProjectRequest request) {
        
        logger.info("Received project retrieval request for project: {}", request.getProjectId());
        
        // Validate auth token
        String userId = projectService.validateAuthToken(authToken);
        if (userId == null) {
            logger.warn("Invalid or missing auth token for project: {}", request.getProjectId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ProjectResponse(false, "Invalid or missing authorization token", request.getProjectId()));
        }
        
        // Retrieve and log project
        com.armikom.zen.model.Project project = projectService.retrieveAndLogProject(request.getProjectId(), userId);
        
        if (project != null) {
            logger.info("Project retrieved and logged successfully: {} for user: {}", 
                request.getProjectId(), userId);
            return ResponseEntity.ok(new ProjectResponse(true, 
                "Project retrieved and logged successfully", request.getProjectId(), project));
        } else {
            logger.warn("Project not found or error occurred: {} for user: {}", 
                request.getProjectId(), userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ProjectResponse(false, "Project not found", request.getProjectId()));
        }
    }
    
    @GetMapping("/firebase/status")
    @Operation(
        summary = "Check Firebase Status", 
        description = "Check if Firebase is properly initialized and available"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Firebase status retrieved successfully")
    })
    public ResponseEntity<ProjectResponse> getFirebaseStatus() {
        boolean isAvailable = projectService.isFirebaseAvailable();
        String message = isAvailable ? "Firebase is available" : "Firebase is not available";
        
        logger.info("Firebase status check: {}", message);
        
        return ResponseEntity.ok(new ProjectResponse(isAvailable, message, null));
    }
}
