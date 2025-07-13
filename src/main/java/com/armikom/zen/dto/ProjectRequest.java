package com.armikom.zen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request object for project operations")
public class ProjectRequest {
    
    @Schema(description = "Project ID to retrieve", example = "project-123")
    @NotBlank(message = "Project ID cannot be blank")
    private String projectId;
    
    public ProjectRequest() {}
    
    public ProjectRequest(String projectId) {
        this.projectId = projectId;
    }
    
    public String getProjectId() {
        return projectId;
    }
    
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
    
    @Override
    public String toString() {
        return "ProjectRequest{" +
                "projectId='" + projectId + '\'' +
                '}';
    }
} 