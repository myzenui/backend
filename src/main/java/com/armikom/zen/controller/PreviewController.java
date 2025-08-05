package com.armikom.zen.controller;

import com.armikom.zen.service.PreviewService;
import com.armikom.zen.service.ProjectService;
import com.armikom.zen.model.Project;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/preview")
public class PreviewController {

    private static final Logger logger = LoggerFactory.getLogger(PreviewController.class);

    private final PreviewService previewService;
    private final ProjectService projectService;

    public PreviewController(PreviewService previewService, ProjectService projectService) {
        this.previewService = previewService;
        this.projectService = projectService;
    }

    /**
     * Generate a preview for a project
     * @param projectId The project ID
     * @param authHeader The authorization header containing Firebase token
     * @return Response with preview location or error
     */
    @PostMapping("/generate/{projectId}")
    public ResponseEntity<Map<String, Object>> generatePreview(
            @PathVariable String projectId,
            @RequestHeader("Authorization") String authHeader) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Preview generation requested for project: {}", projectId);

            // Validate auth token and get user ID
            String userId = projectService.validateAuthToken(authHeader);
            if (userId == null) {
                logger.warn("Invalid auth token for preview generation");
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(401).body(response);
            }

            // Get project to validate access and get business model
            Project project = projectService.retrieveAndLogProject(projectId, userId);
            if (project == null) {
                logger.warn("Project not found for preview: {}", projectId);
                response.put("success", false);
                response.put("message", "Project not found");
                return ResponseEntity.notFound().build();
            }

            // Check if business model exists
            String plantUml = project.getBusinessModel();
            if (plantUml == null || plantUml.trim().isEmpty()) {
                logger.warn("No business model found for project: {}", projectId);
                response.put("success", false);
                response.put("message", "No business model found. Please generate a model first.");
                return ResponseEntity.badRequest().body(response);
            }

            // Generate preview
            boolean success = previewService.generatePreview(projectId, plantUml);
            if (success) {
                String previewLocation = previewService.getPreviewLocation(projectId);
                response.put("success", true);
                response.put("message", "Preview generated successfully");
                response.put("previewLocation", previewLocation);
                logger.info("Preview generated successfully for project: {} at {}", projectId, previewLocation);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Failed to generate preview");
                return ResponseEntity.internalServerError().body(response);
            }

        } catch (Exception e) {
            logger.error("Error generating preview for project: {}", projectId, e);
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get preview status and location for a project
     * @param projectId The project ID
     * @param authHeader The authorization header containing Firebase token
     * @return Response with preview information
     */
    @GetMapping("/status/{projectId}")
    public ResponseEntity<Map<String, Object>> getPreviewStatus(
            @PathVariable String projectId,
            @RequestHeader("Authorization") String authHeader) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate auth token
            String userId = projectService.validateAuthToken(authHeader);
            if (userId == null) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(401).body(response);
            }

            // Validate project access
            Project project = projectService.retrieveAndLogProject(projectId, userId);
            if (project == null) {
                response.put("success", false);
                response.put("message", "Project not found");
                return ResponseEntity.notFound().build();
            }

            String previewLocation = previewService.getPreviewLocation(projectId);
            response.put("success", true);
            response.put("previewLocation", previewLocation);
            
            // Check if preview directory exists
            java.io.File previewDir = new java.io.File(previewLocation);
            response.put("previewExists", previewDir.exists());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting preview status for project: {}", projectId, e);
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Clean up preview files for a project
     * @param projectId The project ID
     * @param authHeader The authorization header containing Firebase token
     * @return Response indicating success or failure
     */
    @DeleteMapping("/cleanup/{projectId}")
    public ResponseEntity<Map<String, Object>> cleanupPreview(
            @PathVariable String projectId,
            @RequestHeader("Authorization") String authHeader) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate auth token
            String userId = projectService.validateAuthToken(authHeader);
            if (userId == null) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(401).body(response);
            }

            // Validate project access
            Project project = projectService.retrieveAndLogProject(projectId, userId);
            if (project == null) {
                response.put("success", false);
                response.put("message", "Project not found");
                return ResponseEntity.notFound().build();
            }

            boolean success = previewService.cleanupPreview(projectId);
            if (success) {
                response.put("success", true);
                response.put("message", "Preview cleanup completed");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Failed to cleanup preview");
                return ResponseEntity.internalServerError().body(response);
            }

        } catch (Exception e) {
            logger.error("Error cleaning up preview for project: {}", projectId, e);
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}