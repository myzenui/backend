package com.armikom.zen.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Job model representing a Firestore job document")
public class Job {
    
    @Schema(description = "Job ID", example = "job123")
    private String id;
    
    @Schema(description = "Project ID associated with the job", example = "project123")
    private String projectId;
    
    @Schema(description = "User ID who owns this job", example = "user123")
    private String userId;
    
    @Schema(description = "Status of the job", example = "pending")
    private String status;

    @Schema(description = "Type of the job", example = "generate")
    private String type;
    
    @Schema(description = "Creation date of the job")
    private LocalDateTime creationDate;
    
    public Job() {}
    
    public Job(String id, String projectId, String userId, String status, String type, LocalDateTime creationDate) {
        this.id = id;
        this.projectId = projectId;
        this.userId = userId;
        this.status = status;
        this.type = type;
        this.creationDate = creationDate;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getProjectId() {
        return projectId;
    }
    
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
    
    public LocalDateTime getCreationDate() {
        return creationDate;
    }
    
    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }
    
    @Override
    public String toString() {
        return "Job{" +
                "id='" + id + '\'' +
                ", projectId='" + projectId + '\'' +
                ", userId='" + userId + '\'' +
                ", status='" + status + '\'' +
                ", type='" + type + '\'' +
                ", creationDate=" + creationDate +
                '}';
    }
}
