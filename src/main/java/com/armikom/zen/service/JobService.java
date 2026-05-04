package com.armikom.zen.service;

import com.google.cloud.firestore.DocumentChange;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreException;
import com.google.cloud.firestore.ListenerRegistration;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.FirebaseApp;
import com.armikom.zen.enums.DatabaseEnvironment;
import com.armikom.zen.model.Job;
import com.armikom.zen.model.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class JobService {

    private static final Logger logger = LoggerFactory.getLogger(JobService.class);
    private static final String JOBS_COLLECTION = "jobs";

    private final ProjectService projectService;
    private final PreviewService previewService;
    private final AIService aiService;
    private final DatabaseService databaseService;
    private final FirebaseApp firebaseApp;
    private final Firestore firestore;

    @Value("${job.service.enabled:false}")
    private boolean jobServiceEnabled;

    private volatile ListenerRegistration listenerRegistration;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean listenerStarted = new AtomicBoolean(false);
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    // Track processing jobs to avoid duplicate processing
    private final Map<String, CompletableFuture<Void>> processingJobs = new ConcurrentHashMap<>();

    public JobService(
            ProjectService projectService,
            PreviewService previewService,
            AIService aiService,
            DatabaseService databaseService,
            FirebaseApp firebaseApp,
            Firestore firestore) {
        this.projectService = projectService;
        this.previewService = previewService;
        this.aiService = aiService;
        this.databaseService = databaseService;
        this.firebaseApp = firebaseApp;
        this.firestore = firestore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (initialized.compareAndSet(false, true)) {
            if (!jobServiceEnabled) {
                logger.info("JobService is disabled via configuration (job.service.enabled=false)");
                return;
            }
            
            try {
                // Small delay to ensure Firestore is fully ready
                Thread.sleep(1000);
                startJobListener();
                logger.info("JobService initialized successfully and listening for jobs");
            } catch (Exception e) {
                logger.error("Failed to initialize JobService", e);
                initialized.set(false);
            }
        }
    }

    private void startJobListener() {
        if (shutdownRequested.get()) {
            logger.info("Shutdown requested, skipping job listener initialization");
            return;
        }

        // Prevent duplicate listeners - check if listener is already started
        if (!listenerStarted.compareAndSet(false, true)) {
            logger.info("Job listener already started, skipping duplicate initialization");
            return;
        }

        logger.info("JobService job listener starting..");
        if (firestore == null) {
            logger.error("Cannot start job listener: Firestore not initialized");
            listenerStarted.set(false); // Reset flag on error
            return;
        }

        // Cleanup any existing listener before creating new one
        cleanupExistingListener();

        try {
            Query query = firestore.collection(JOBS_COLLECTION).whereEqualTo("status", "queued");

            listenerRegistration = query.addSnapshotListener(new com.google.cloud.firestore.EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(QuerySnapshot snapshots, FirestoreException e) {
                    if (shutdownRequested.get()) {
                        logger.debug("Shutdown requested, ignoring snapshot event");
                        return;
                    }

                    if (e != null) {
                        if (e.getCause() instanceof RejectedExecutionException) {
                            logger.warn("Firestore listener rejected execution - likely shutting down");
                            return;
                        }
                        logger.error("Error listening to jobs collection", e);
                        return;
                    }

                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (shutdownRequested.get()) {
                            logger.debug("Shutdown requested, stopping job processing");
                            return;
                        }

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
        } catch (RejectedExecutionException e) {
            logger.warn("Failed to start job listener due to executor shutdown: {}", e.getMessage());
            listenerStarted.set(false); // Reset flag on failure
        } catch (Exception e) {
            logger.error("Unexpected error starting job listener", e);
            listenerStarted.set(false); // Reset flag on failure
        }
    }

    private void cleanupExistingListener() {
        if (listenerRegistration != null) {
            try {
                logger.debug("Cleaning up existing Firebase listener");
                listenerRegistration.remove();
                listenerRegistration = null;
            } catch (Exception e) {
                logger.warn("Error cleaning up existing listener: {}", e.getMessage());
            }
        }
    }

    private void handleNewJob(QueryDocumentSnapshot document) {
        if (shutdownRequested.get()) {
            logger.debug("Shutdown requested, skipping job: {}", document.getId());
            return;
        }

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
        if (shutdownRequested.get()) {
            logger.info("Shutdown requested, skipping job processing: {}", job.getId());
            return CompletableFuture.completedFuture(null);
        }

        try {
            logger.info("Starting background task for job: {}", job.getId());

            if ("generate".equals(job.getType())) {
                processGenerateJob(job);
            } else if ("preview".equals(job.getType())) {
                processPreviewJob(job);
            } else if ("reset_database".equals(job.getType())) {
                processResetDatabaseJob(job);
            } else {
                logger.warn("Unknown job type: {}", job.getType());
                updateJobStatus(job.getId(), "failed", "Unknown job type");
            }

            return CompletableFuture.completedFuture(null);
        } catch (InterruptedException e) {
            logger.info("Job processing was interrupted: {}", job.getId());
            updateJobStatus(job.getId(), "cancelled", "Job was cancelled");
            Thread.currentThread().interrupt(); // Restore interrupted status
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            logger.error("Error in background task for job: {}", job.getId(), e);
            updateJobStatus(job.getId(), "failed", "Error: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    private void processGenerateJob(Job job) throws Exception {
        // Check for interruption before starting
        if (Thread.currentThread().isInterrupted()) {
            logger.info("Thread interrupted, cancelling job processing: {}", job.getId());
            throw new InterruptedException("Job processing was cancelled");
        }

        // Get project information
        Project project = projectService.retrieveAndLogProject(job.getProjectId(), job.getUserId());
        if (project == null) {
            logger.error("Project not found for job {}: projectId={}, userId={}",
                    job.getId(), job.getProjectId(), job.getUserId());
            updateJobStatus(job.getId(), "failed", "Project not found");
            return;
        }

        // Check for interruption before AI call
        if (Thread.currentThread().isInterrupted()) {
            logger.info("Thread interrupted, cancelling job processing: {}", job.getId());
            throw new InterruptedException("Job processing was cancelled");
        }

        // Generate PlantUML diagram using AIService
        String plantUmlDiagram = aiService.generatePlantUmlForProject(project);
        if (plantUmlDiagram == null || plantUmlDiagram.trim().isEmpty()) {
            logger.error("Failed to generate PlantUML diagram for job: {}", job.getId());
            updateJobStatus(job.getId(), "failed", "Failed to generate PlantUML diagram");
            return;
        }

        logger.info("Generated PlantUML diagram for job {}", job.getId());

        // Check for interruption before database updates
        if (Thread.currentThread().isInterrupted()) {
            logger.info("Thread interrupted, cancelling job processing: {}", job.getId());
            throw new InterruptedException("Job processing was cancelled");
        }

        // Update project with the generated PlantUML business class model
        updateProjectBusinessModel(job.getProjectId(), plantUmlDiagram);

        // Update job with the generated PlantUML diagram
        updateJobWithResult(job.getId(), plantUmlDiagram);

        logger.info("Background task completed successfully for job: {}", job.getId());
    }

    private void processPreviewJob(Job job) {
        try {
            // Check for interruption before starting
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Thread interrupted, cancelling preview job processing: {}", job.getId());
                throw new InterruptedException("Preview job processing was cancelled");
            }

            logger.info("Processing preview job: {}", job.getId());

            // Get project information
            Project project = projectService.retrieveAndLogProject(job.getProjectId(), job.getUserId());
            if (project == null) {
                logger.error("Project not found for preview job {}: projectId={}, userId={}",
                        job.getId(), job.getProjectId(), job.getUserId());
                updateJobStatus(job.getId(), "failed", "Project not found");
                return;
            }

            // Get the business model (PlantUML) from the project
            String plantUml = project.getBusinessModel();
            if (plantUml == null || plantUml.trim().isEmpty()) {
                logger.error("No business model found for preview job {}: projectId={}",
                        job.getId(), job.getProjectId());
                updateJobStatus(job.getId(), "failed", "No business model found");
                return;
            }

            // Check for interruption before preview generation
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Thread interrupted, cancelling preview job processing: {}", job.getId());
                throw new InterruptedException("Preview job processing was cancelled");
            }

            // Generate preview using PreviewService with buffered Firestore logging
            IJobLog jobLog = new JobLog(firestore, job.getId());
            boolean success = previewService.generatePreview(job.getProjectId(), plantUml, jobLog);
            if (success) {
                String previewLocation = previewService.getPreviewLocation(job.getProjectId());
                updateJobStatus(job.getId(), "completed", "Preview generated at: " + previewLocation);
                logger.info("Preview job completed successfully for job: {}", job.getId());
            } else {
                updateJobStatus(job.getId(), "failed", "Failed to generate preview");
                logger.error("Preview job failed for job: {}", job.getId());
            }

        } catch (InterruptedException e) {
            logger.info("Preview job processing was interrupted: {}", job.getId());
            updateJobStatus(job.getId(), "cancelled", "Job was cancelled");
            Thread.currentThread().interrupt(); // Restore interrupted status
        } catch (Exception e) {
            logger.error("Error processing preview job: {}", job.getId(), e);
            updateJobStatus(job.getId(), "failed", "Error: " + e.getMessage());
        }
    }

    private void processResetDatabaseJob(Job job) {
        IJobLog jobLog = new JobLog(firestore, job.getId());
        try {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Reset database job was cancelled");
            }

            logger.info("Processing reset_database job: {}", job.getId());
            jobLog.log("INFO", "Starting database reset for project " + job.getProjectId());

            // Resolve the actual project id (database name) from the Firestore document
            String projectId = resolveProjectId(job.getProjectId());
            if (projectId == null || projectId.trim().isEmpty()) {
                logger.error("Could not resolve project id for reset_database job {}: firestoreDocId={}",
                        job.getId(), job.getProjectId());
                jobLog.log("ERROR", "Could not resolve project id");
                updateJobStatus(job.getId(), "failed", "Could not resolve project id");
                return;
            }

            // Drop all tables so EF Core migrations can re-create them cleanly
            jobLog.log("INFO", "Dropping all tables in database: " + projectId);
            boolean dropped = databaseService.dropAllTables(DatabaseEnvironment.PREVIEW, projectId);
            if (!dropped) {
                jobLog.log("ERROR", "Failed to drop tables in database: " + projectId);
                updateJobStatus(job.getId(), "failed", "Failed to drop database tables");
                return;
            }
            jobLog.log("INFO", "All tables dropped successfully");

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Reset database job was cancelled");
            }

            // Restart the preview container so EF Core migrations run from scratch
            jobLog.log("INFO", "Restarting preview container for project: " + projectId);
            boolean restarted = previewService.restartContainer(projectId);
            if (!restarted) {
                jobLog.log("WARN", "Container restart failed or container not found. A fresh preview deployment may be needed.");
            } else {
                jobLog.log("INFO", "Preview container restarted — migrations will run on startup");
            }

            updateJobStatus(job.getId(), "completed", "Database reset completed successfully");
            jobLog.log("INFO", "Database reset completed successfully");
            logger.info("Reset database job completed for job: {}", job.getId());

        } catch (InterruptedException e) {
            logger.info("Reset database job was interrupted: {}", job.getId());
            updateJobStatus(job.getId(), "cancelled", "Job was cancelled");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Error processing reset_database job: {}", job.getId(), e);
            jobLog.log("ERROR", "Error during database reset: " + e.getMessage());
            updateJobStatus(job.getId(), "failed", "Error: " + e.getMessage());
        } finally {
            try { jobLog.close(); } catch (Exception ignore) { }
        }
    }

    /**
     * Resolves the actual project id (used as database name) from the Firestore project document's
     * `id` field. Falls back to the Firestore document id if the field is absent.
     */
    private String resolveProjectId(String firestoreDocumentId) {
        try {
            DocumentReference ref = firestore.collection("projects").document(firestoreDocumentId);
            com.google.cloud.firestore.DocumentSnapshot snap = ref.get().get();
            if (snap.exists()) {
                Object value = snap.get("id");
                if (value != null) {
                    return String.valueOf(value).trim();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to resolve project id from Firestore for doc {}: {}", firestoreDocumentId, e.getMessage());
        }
        return firestoreDocumentId;
    }

    private void updateProjectBusinessModel(String projectId, String plantUmlDiagram) {
        if (shutdownRequested.get()) {
            logger.debug("Shutdown requested, skipping project update: {}", projectId);
            return;
        }

        try {
            DocumentReference projectRef = firestore.collection("projects").document(projectId);

            Map<String, Object> updates = new HashMap<>();
            updates.put("businessModel", plantUmlDiagram);
            updates.put("updatedAt", new Date());

            projectRef.update(updates).get();
            logger.info("Updated project {} business model with PlantUML diagram", projectId);

        } catch (ExecutionException | InterruptedException e) {
            if (e.getCause() instanceof IllegalStateException &&
                e.getCause().getMessage().contains("Firestore client has already been closed")) {
                logger.warn("Firestore client closed during project update: {}", projectId);
            } else {
                logger.error("Failed to update project business model for project: {}", projectId, e);
            }
        } catch (Exception e) {
            logger.error("Unexpected error updating project business model: {}", projectId, e);
        }
    }

    private void updateJobWithResult(String jobId, String plantUmlDiagram) {
        if (shutdownRequested.get()) {
            logger.debug("Shutdown requested, skipping job update: {}", jobId);
            return;
        }

        try {
            DocumentReference jobRef = firestore.collection(JOBS_COLLECTION).document(jobId);

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "completed");
            updates.put("result", plantUmlDiagram);
            updates.put("updatedAt", new Date());

            jobRef.update(updates).get();
            logger.info("Updated job {} with PlantUML result", jobId);

        } catch (ExecutionException | InterruptedException e) {
            if (e.getCause() instanceof IllegalStateException &&
                e.getCause().getMessage().contains("Firestore client has already been closed")) {
                logger.warn("Firestore client closed during job update: {}", jobId);
            } else {
                logger.error("Failed to update job with result for job: {}", jobId, e);
            }
        } catch (Exception e) {
            logger.error("Unexpected error updating job result: {}", jobId, e);
        }
    }

    private void updateJobStatus(String jobId, String status) {
        updateJobStatus(jobId, status, null);
    }

    private void updateJobStatus(String jobId, String status, String errorMessage) {
        if (shutdownRequested.get()) {
            logger.debug("Shutdown requested, skipping job status update: {}", jobId);
            return;
        }

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
            if (e.getCause() instanceof IllegalStateException &&
                e.getCause().getMessage().contains("Firestore client has already been closed")) {
                logger.warn("Firestore client closed during job status update: {}", jobId);
            } else {
                logger.error("Failed to update job status for job: {}", jobId, e);
            }
        } catch (Exception e) {
            logger.error("Unexpected error updating job status: {}", jobId, e);
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
            job.setType((String) data.get("type"));

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
        shutdownRequested.set(true);
        logger.info("JobService shutdown requested");

        // Stop the listener first
        if (listenerRegistration != null) {
            try {
                listenerRegistration.remove();
                listenerRegistration = null;
                logger.info("Stopped listening to jobs collection");
            } catch (Exception e) {
                logger.warn("Error stopping job listener: {}", e.getMessage());
            }
        }
        
        // Reset listener state
        listenerStarted.set(false);

        // Cancel any ongoing processing
        for (Map.Entry<String, CompletableFuture<Void>> entry : processingJobs.entrySet()) {
            try {
                CompletableFuture<Void> future = entry.getValue();
                if (!future.isDone()) {
                    future.cancel(true);
                    logger.debug("Cancelled processing job: {}", entry.getKey());
                }
            } catch (Exception e) {
                logger.warn("Error cancelling job {}: {}", entry.getKey(), e.getMessage());
            }
        }
        processingJobs.clear();

        initialized.set(false);
        logger.info("JobService cleanup completed");
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
        return firebaseApp != null && firestore != null && !shutdownRequested.get();
    }

    /**
     * Check if the job service is enabled via configuration
     * @return true if job service is enabled, false otherwise
     */
    public boolean isJobServiceEnabled() {
        return jobServiceEnabled;
    }
}


