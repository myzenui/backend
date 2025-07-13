package com.armikom.zen.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentChange;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.EventListener;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreException;
import com.google.cloud.firestore.ListenerRegistration;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.armikom.zen.model.Job;
import com.armikom.zen.model.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Service
@EnableAsync
public class AIService {
    
    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    private static final String JOBS_COLLECTION = "jobs";
    
    private final ProjectService projectService;
    private final ChatClient chatClient;
    private final FirebaseApp firebaseApp;
    private final Firestore firestore;
    private ListenerRegistration listenerRegistration;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Track processing jobs to avoid duplicate processing
    private final Map<String, CompletableFuture<Void>> processingJobs = new ConcurrentHashMap<>();
    
    public AIService(
            ProjectService projectService,
            ChatModel chatModel,
            FirebaseApp firebaseApp,
            Firestore firestore) {
        this.projectService = projectService;
        this.chatClient = ChatClient.builder(chatModel).build();
        this.firebaseApp = firebaseApp;
        this.firestore = firestore;
    }
    
    @PostConstruct
    public void init() {
        try {
            startJobListener();
            logger.info("AIService initialized successfully and listening for jobs");
        } catch (Exception e) {
            logger.error("Failed to initialize AIService", e);
        }
    }
    
    
    private void startJobListener() {
        if (firestore == null) {
            logger.error("Cannot start job listener: Firestore not initialized");
            return;
        }
        
        Query query = firestore.collection(JOBS_COLLECTION).whereEqualTo("status", "queued");
        
        listenerRegistration = query.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot snapshots, FirestoreException e) {
                if (e != null) {
                    logger.error("Error listening to jobs collection", e);
                    return;
                }
                
                for (DocumentChange dc : snapshots.getDocumentChanges()) {
                    switch (dc.getType()) {
                        case ADDED:
                            handleNewJob(dc.getDocument());
                            break;
                        case MODIFIED:
                            logger.debug("Modified job: {}", dc.getDocument().getId());
                            break;
                        case REMOVED:
                            logger.debug("Removed job: {}", dc.getDocument().getId());
                            break;
                    }
                }
            }
        });
        
        logger.info("Started listening to jobs collection");
    }
    
    private void handleNewJob(QueryDocumentSnapshot document) {
        try {
            Job job = convertToJob(document);
            if (job != null) {
                logger.info("New job detected: {}", job);
                
                // Check if job is already being processed
                if (!processingJobs.containsKey(job.getId())) {
                    // Start background task for this job
                    CompletableFuture<Void> future = processJobAsync(job);
                    processingJobs.put(job.getId(), future);
                    
                    // Remove from processing map when complete
                    future.whenComplete((result, throwable) -> {
                        processingJobs.remove(job.getId());
                        if (throwable != null) {
                            logger.error("Error processing job {}: {}", job.getId(), throwable.getMessage());
                        }
                    });
                }
            }
        } catch (Exception e) {
            logger.error("Error handling new job: {}", document.getId(), e);
        }
    }
    
    @Async
    public CompletableFuture<Void> processJobAsync(Job job) {
        return CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting AI-powered background task for job: {}", job.getId());
                
                // Get project information
                com.armikom.zen.model.Project project = projectService.retrieveAndLogProject(job.getProjectId(), job.getUserId());
                if (project == null) {
                    logger.error("Project not found for job {}: projectId={}, userId={}", 
                        job.getId(), job.getProjectId(), job.getUserId());
                    updateJobStatus(job.getId(), "failed", "Project not found");
                    return;
                }
                
                // Build project description from project data
                String projectDescription = buildProjectDescription(project);
                logger.info("Generated project description for job {}: {}", job.getId(), projectDescription);
                
                // Generate PlantUML diagram using Vertex AI
                String plantUmlDiagram = generatePlantUmlDiagram(projectDescription);
                if (plantUmlDiagram == null || plantUmlDiagram.trim().isEmpty()) {
                    logger.error("Failed to generate PlantUML diagram for job: {}", job.getId());
                    updateJobStatus(job.getId(), "failed", "Failed to generate PlantUML diagram");
                    return;
                }
                
                logger.info("Generated PlantUML diagram for job {}: {}", job.getId(), plantUmlDiagram);
                
                // Update project with the generated PlantUML business class model
                updateProjectBusinessModel(job.getProjectId(), plantUmlDiagram);
                
                // Update job with the generated PlantUML diagram
                updateJobWithResult(job.getId(), plantUmlDiagram);
                
                logger.info("AI-powered background task completed successfully for job: {}", job.getId());
                
            } catch (Exception e) {
                logger.error("Error in AI-powered background task for job: {}", job.getId(), e);
                updateJobStatus(job.getId(), "failed", "Error: " + e.getMessage());
            }
        });
    }
    
    private String buildProjectDescription(Project project) {
        StringBuilder description = new StringBuilder();
        
        description.append("Project Name: ").append(project.getName()).append("\n");
        //description.append("Business Model: ").append(project.getBusinessModel()).append("\n");
        description.append("Description: ").append(project.getDescription()).append("\n");
        
        if (project.getUserStories() != null && !project.getUserStories().isEmpty()) {
            description.append("User Stories:\n");
            for (int i = 0; i < project.getUserStories().size(); i++) {
                description.append("- ").append(project.getUserStories().get(i)).append("\n");
            }
        }
        
        return description.toString();
    }
    
    private String generatePlantUmlDiagram(String projectDescription) {
        try {
            String systemInstruction = "You are an expert in PlantUML and software design. Your task is to generate PlantUML class diagrams from project descriptions.";
            
            String prompt = """
                You will be provided with a project description. Your goal is to create a PlantUML class diagram representing the system described in the project description.

                Follow these steps:

                1. Carefully read and understand the project description.
                2. Identify the key classes in the system. Classes typically represent the main entities or concepts in the project description.
                3. Identify the attributes of each class. Attributes are the properties or data associated with each class.
                4. Identify the relationships between the classes. Relationships can be associations, aggregations, compositions, generalizations (inheritance), or dependencies.
                5. Translate the classes, attributes, and relationships into PlantUML syntax.
                6. Output the PlantUML code directly. Do not include any introductory or explanatory text.

                Here is the project description:
                %s

                Example of PlantUML syntax for class diagrams:

                @startuml
                class ClassName {
                  - attribute1: type
                  - attribute2: type
                  + method1()
                  + method2()
                }

                ClassA -- ClassB : association
                ClassC *-- ClassD : aggregation
                ClassE o-- ClassF : composition
                ClassG <|-- ClassH : generalization
                ClassI ..> ClassJ : dependency
                @enduml
                """.formatted(projectDescription);
            
            String response = chatClient.prompt()
                .system(systemInstruction)
                .user(prompt)
                .call()
                .content();
            
            logger.info("Vertex AI response received: {}", response);
            return response;
            
        } catch (Exception e) {
            logger.error("Error generating PlantUML diagram", e);
            return null;
        }
    }
    
    private void updateProjectBusinessModel(String projectId, String plantUmlDiagram) {
        try {
            DocumentReference projectRef = firestore.collection("projects").document(projectId);
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("businessModel", plantUmlDiagram);
            updates.put("updatedAt", new Date());
            
            projectRef.update(updates).get();
            logger.info("Updated project {} business model with PlantUML diagram", projectId);
            
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Failed to update project business model for project: {}", projectId, e);
        }
    }
    
    private void updateJobWithResult(String jobId, String plantUmlDiagram) {
        try {
            DocumentReference jobRef = firestore.collection(JOBS_COLLECTION).document(jobId);
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "completed");
            updates.put("result", plantUmlDiagram);
            updates.put("updatedAt", new Date());
            
            jobRef.update(updates).get();
            logger.info("Updated job {} with PlantUML result", jobId);
            
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Failed to update job with result for job: {}", jobId, e);
        }
    }
    
    private void updateJobStatus(String jobId, String status) {
        updateJobStatus(jobId, status, null);
    }
    
    private void updateJobStatus(String jobId, String status, String errorMessage) {
        try {
            DocumentReference jobRef = firestore.collection(JOBS_COLLECTION).document(jobId);
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", status);
            updates.put("updatedAt", new Date());
            
            if (errorMessage != null) {
                updates.put("errorMessage", errorMessage);
            }
            
            jobRef.update(updates).get();
            logger.info("Updated job {} status to: {}", jobId, status);
            
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Failed to update job status for job: {}", jobId, e);
        }
    }
    
    private Job convertToJob(QueryDocumentSnapshot document) {
        try {
            Map<String, Object> data = document.getData();
            Job job = new Job();
            
            job.setId(document.getId());
            job.setProjectId((String) data.get("projectId"));
            job.setUserId((String) data.get("userId"));
            job.setStatus((String) data.get("status"));
            
            // Handle creation_date field
            Object creationDate = data.get("creation_date");
            if (creationDate instanceof Date) {
                job.setCreationDate(((Date) creationDate).toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime());
            } else if (creationDate instanceof com.google.cloud.Timestamp) {
                job.setCreationDate(((com.google.cloud.Timestamp) creationDate).toDate().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime());
            }
            
            return job;
            
        } catch (Exception e) {
            logger.error("Error converting document to Job: {}", document.getId(), e);
            return null;
        }
    }
    
    @PreDestroy
    public void cleanup() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            logger.info("Stopped listening to jobs collection");
        }
        
        // Cancel any ongoing processing
        for (CompletableFuture<Void> future : processingJobs.values()) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
        processingJobs.clear();
        
        logger.info("AIService cleanup completed");
    }
    
    /**
     * Get the current number of jobs being processed
     * @return The number of jobs currently being processed
     */
    public int getProcessingJobCount() {
        return processingJobs.size();
    }
    
    /**
     * Check if Firebase is available
     * @return true if Firebase is initialized and available
     */
    public boolean isFirebaseAvailable() {
        return firebaseApp != null && firestore != null;
    }
}
