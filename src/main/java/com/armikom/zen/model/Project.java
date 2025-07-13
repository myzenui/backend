package com.armikom.zen.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Project model representing a Firestore project document")
public class Project {
    
    @Schema(description = "Business model of the project", example = "SaaS")
    private String businessModel;
    
    @Schema(description = "Name of the project", example = "Deneme")
    private String name;
    
    @Schema(description = "HTML description of the project", example = "<p>Açıklama</p>")
    private String description;
    
    @Schema(description = "List of user stories for the project")
    private List<String> userStories;
    
    @Schema(description = "User ID who owns this project", example = "jOZjq6mWVahEChuHBwzhvuOYMGt2")
    private String userId;
    
    public Project() {}
    
    public Project(String businessModel, String name, String description, List<String> userStories, String userId) {
        this.businessModel = businessModel;
        this.name = name;
        this.description = description;
        this.userStories = userStories;
        this.userId = userId;
    }
    
    public String getBusinessModel() {
        return businessModel;
    }
    
    public void setBusinessModel(String businessModel) {
        this.businessModel = businessModel;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<String> getUserStories() {
        return userStories;
    }
    
    public void setUserStories(List<String> userStories) {
        this.userStories = userStories;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    @Override
    public String toString() {
        return "Project{" +
                "businessModel='" + businessModel + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", userStories=" + userStories +
                ", userId='" + userId + '\'' +
                '}';
    }
} 