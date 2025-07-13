package com.armikom.zen.dto;

import com.armikom.zen.model.Project;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response object for project operations")
public class ProjectResponse {
    
    @Schema(description = "Whether the operation was successful", example = "true")
    private boolean success;
    
    @Schema(description = "Response message", example = "Project retrieved and logged successfully")
    private String message;
    
    @Schema(description = "Project ID that was processed", example = "project-123")
    private String projectId;
    
    @Schema(description = "Project data if retrieved successfully")
    private Project project;
    
    public ProjectResponse() {}
    
    public ProjectResponse(boolean success, String message, String projectId) {
        this.success = success;
        this.message = message;
        this.projectId = projectId;
    }
    
    public ProjectResponse(boolean success, String message, String projectId, Project project) {
        this.success = success;
        this.message = message;
        this.projectId = projectId;
        this.project = project;
    }
    
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
    
    public String getProjectId() {
        return projectId;
    }
    
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
    
    public Project getProject() {
        return project;
    }
    
    public void setProject(Project project) {
        this.project = project;
    }
    
    @Override
    public String toString() {
        return "ProjectResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", projectId='" + projectId + '\'' +
                ", project=" + project +
                '}';
    }
} 