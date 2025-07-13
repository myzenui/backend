package com.armikom.zen.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;
import com.armikom.zen.model.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class ProjectService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    private static final String PROJECTS_COLLECTION = "projects";
    
    @Value("${firebase.credentials.json:}")
    private String firebaseCredentialsJson;
    
    @Value("${firebase.project.id:}")
    private String firebaseProjectId;
    
    private FirebaseApp firebaseApp;
    private Firestore firestore;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    public void init() {
        try {
            initializeFirebase();
            logger.info("Firebase initialized successfully for project: {}", firebaseProjectId);
        } catch (Exception e) {
            logger.error("Failed to initialize Firebase", e);
        }
    }
    
    private void initializeFirebase() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder();
            
            // Configure credentials
            if (firebaseCredentialsJson != null && !firebaseCredentialsJson.trim().isEmpty()) {
                // Use service account JSON from configuration
                logger.info("Using Firebase credentials from configuration (JSON)");
                ByteArrayInputStream credentialsStream = new ByteArrayInputStream(
                    firebaseCredentialsJson.getBytes());
                GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
                optionsBuilder.setCredentials(credentials);
            } else {
                // Use default credentials (from gcloud auth, environment, or metadata server)
                logger.info("Using Firebase Application Default Credentials (gcloud auth or environment)");
                optionsBuilder.setCredentials(GoogleCredentials.getApplicationDefault());
            }
            
            // Set project ID if provided
            if (firebaseProjectId != null && !firebaseProjectId.trim().isEmpty()) {
                optionsBuilder.setProjectId(firebaseProjectId);
                logger.info("Using Firebase project ID: {}", firebaseProjectId);
            }
            
            firebaseApp = FirebaseApp.initializeApp(optionsBuilder.build());
        } else {
            firebaseApp = FirebaseApp.getInstance();
        }
        
        firestore = FirestoreClient.getFirestore(firebaseApp);
    }
    
    @PreDestroy
    public void cleanup() {
        if (firebaseApp != null) {
            try {
                firebaseApp.delete();
                logger.info("Firebase app cleaned up successfully");
            } catch (Exception e) {
                logger.warn("Error cleaning up Firebase app", e);
            }
        }
    }
    
    /**
     * Validates the Firebase auth token and returns the user ID
     * @param authToken The Firebase auth token from the Authorization header
     * @return The user ID if token is valid, null otherwise
     */
    public String validateAuthToken(String authToken) {
        if (authToken == null || authToken.trim().isEmpty()) {
            logger.warn("Auth token is null or empty");
            return null;
        }
        
        try {
            // Remove "Bearer " prefix if present
            String token = authToken.startsWith("Bearer ") ? 
                authToken.substring(7) : authToken;
            
            FirebaseToken decodedToken = FirebaseAuth.getInstance(firebaseApp)
                .verifyIdToken(token);
            
            String userId = decodedToken.getUid();
            logger.info("Auth token validated successfully for user: {}", userId);
            return userId;
            
        } catch (FirebaseAuthException e) {
            logger.error("Firebase auth token validation failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error during token validation", e);
            return null;
        }
    }
    
    /**
     * Retrieves a project from Firestore and logs its content
     * @param projectId The project ID to retrieve
     * @param userId The authenticated user ID
     * @return Project object if retrieved successfully, null otherwise
     */
    public Project retrieveAndLogProject(String projectId, String userId) {
        if (projectId == null || projectId.trim().isEmpty()) {
            logger.error("Project ID cannot be null or empty");
            return null;
        }
        
        if (userId == null || userId.trim().isEmpty()) {
            logger.error("User ID cannot be null or empty");
            return null;
        }
        
        try {
            DocumentReference projectRef = firestore.collection(PROJECTS_COLLECTION)
                .document(projectId);
            
            DocumentSnapshot document = projectRef.get().get();
            
            if (!document.exists()) {
                logger.warn("Project not found: {} for user: {}", projectId, userId);
                return null;
            }
            
            // Get the document content
            Map<String, Object> projectData = document.getData();
            
            // Convert to Project object
            Project project = convertToProject(projectData);
            
            // Log basic information
            logger.info("=== PROJECT DOCUMENT RETRIEVED ===");
            logger.info("Project ID: {}", projectId);
            logger.info("User ID: {}", userId);
            logger.info("Document ID: {}", document.getId());
            logger.info("Document exists: {}", document.exists());
            logger.info("Document create time: {}", document.getCreateTime());
            logger.info("Document update time: {}", document.getUpdateTime());
            
            // Log project data as JSON
            if (projectData != null) {
                try {
                    String jsonString = objectMapper.writeValueAsString(projectData);
                    logger.info("Project data as JSON: {}", jsonString);
                } catch (Exception e) {
                    logger.error("Failed to serialize project data to JSON", e);
                    logger.info("Project data (fallback): {}", projectData);
                }
            } else {
                logger.info("Project data is null");
            }
            
            // Log structured project data
            logger.info("=== STRUCTURED PROJECT DATA ===");
            logger.info("Project Name: {}", project.getName());
            logger.info("Business Model: {}", project.getBusinessModel());
            logger.info("Description: {}", project.getDescription());
            logger.info("User Stories: {}", project.getUserStories());
            logger.info("Owner User ID: {}", project.getUserId());
            logger.info("Project Object: {}", project);
            
            logger.info("=== END PROJECT DOCUMENT ===");
            
            return project;
            
        } catch (ExecutionException e) {
            logger.error("Execution error while retrieving project: {} for user: {}", 
                projectId, userId, e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while retrieving project: {} for user: {}", 
                projectId, userId, e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving project: {} for user: {}", 
                projectId, userId, e);
            return null;
        }
    }
    
    /**
     * Converts Firestore document data to Project object
     * @param projectData Map containing project data from Firestore
     * @return Project object
     */
    private Project convertToProject(Map<String, Object> projectData) {
        if (projectData == null) {
            return new Project();
        }
        
        Project project = new Project();
        
        // Set fields with null safety
        project.setBusinessModel((String) projectData.get("businessModel"));
        project.setName((String) projectData.get("name"));
        project.setDescription((String) projectData.get("description"));
        project.setUserId((String) projectData.get("userId"));
        
        // Handle userStories list
        Object userStoriesObj = projectData.get("userStories");
        if (userStoriesObj instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> userStories = (List<String>) userStoriesObj;
            project.setUserStories(userStories);
        }
        
        return project;
    }
    
    /**
     * Checks if Firebase is properly initialized
     * @return true if Firebase is available, false otherwise
     */
    public boolean isFirebaseAvailable() {
        try {
            return firebaseApp != null && firestore != null;
        } catch (Exception e) {
            logger.error("Error checking Firebase availability", e);
            return false;
        }
    }
} 