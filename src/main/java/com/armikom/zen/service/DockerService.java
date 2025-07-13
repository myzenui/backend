package com.armikom.zen.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DockerService {
    
    private static final Logger logger = LoggerFactory.getLogger(DockerService.class);
    private DockerClient dockerClient;
    private boolean dockerAvailable = false;
    
    @PostConstruct
    public void init() {
        try {
            DockerClientConfig cfg = DefaultDockerClientConfig
                    .createDefaultConfigBuilder()
                    // honour $DOCKER_HOST or fall back sensibly
                    // .withDockerHost(
                    //     Optional.ofNullable(System.getenv("DOCKER_HOST"))
                    //             .orElse("unix:///var/run/docker.sock"))
                    .build();
    
            DockerHttpClient http = new ApacheDockerHttpClient.Builder()
                    .dockerHost(cfg.getDockerHost())
                    .build();
    
            dockerClient = DockerClientBuilder.getInstance(cfg).withDockerHttpClient(http).build();
            dockerClient.pingCmd().exec();   // verifies connection
    
            dockerAvailable = true;
        } catch (Exception ex) {
            logger.warn("Docker not available: {}", ex.getMessage());
            dockerAvailable = false;
        }
    }
    
    /**
     * Determine the Docker host to use, checking multiple locations
     * @return Docker host URI
     */
    private String determineDockerHost() {
        // First, check environment variable
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost != null && !dockerHost.trim().isEmpty()) {
            logger.debug("Using DOCKER_HOST environment variable: {}", dockerHost);
            return dockerHost;
        }
        
        // Get current user's home directory for dynamic Colima path
        String userHome = System.getProperty("user.home");
        
        // Check for Colima socket (local development on macOS)
        String colimaSocket = "unix://" + userHome + "/.colima/default/docker.sock";
        if (new java.io.File(userHome + "/.colima/default/docker.sock").exists()) {
            logger.debug("Found Colima Docker socket: {}", colimaSocket);
            return colimaSocket;
        }
        
        // Check for standard Docker socket (production/Linux)
        String standardSocket = "unix:///var/run/docker.sock";
        if (new java.io.File("/var/run/docker.sock").exists()) {
            logger.debug("Found standard Docker socket: {}", standardSocket);
            return standardSocket;
        }
        
        // Check for Docker Desktop on macOS (newer versions)
        String dockerDesktopSocket = "unix://" + userHome + "/.docker/run/docker.sock";
        if (new java.io.File(userHome + "/.docker/run/docker.sock").exists()) {
            logger.debug("Found Docker Desktop socket: {}", dockerDesktopSocket);
            return dockerDesktopSocket;
        }
        
        // Default fallback - let Docker Java library try its defaults
        logger.debug("No Docker socket found, using default configuration");
        return "unix:///var/run/docker.sock"; // Standard fallback
    }
    
    @PreDestroy
    public void cleanup() {
        if (dockerClient != null) {
            try {
                dockerClient.close();
                logger.info("Docker client closed successfully");
            } catch (IOException e) {
                logger.warn("Error closing Docker client", e);
            }
        }
    }
    
    /**
     * Push a Docker image to a registry
     * @param imageTag The full image tag to push (e.g., "europe-west1-docker.pkg.dev/myzenui/image/zen-backend:latest")
     * @return true if push was successful, false otherwise
     */
    public boolean pushDockerImage(String imageTag) {
        if (imageTag == null || imageTag.trim().isEmpty()) {
            logger.error("Image tag cannot be null or empty");
            return false;
        }
        
        logger.info("Pushing Docker image: {}", imageTag);
        
        // First try Docker Java API
        if (dockerClient != null) {
            try {
                return pushWithDockerJavaAPI(imageTag);
            } catch (Exception e) {
                logger.warn("Docker Java API push failed, trying CLI fallback: {}", e.getMessage());
            }
        }
        
        // Fallback to Docker CLI
        return pushWithDockerCLI(imageTag);
    }
    
    /**
     * Get Google Cloud authentication configuration for Docker operations
     * @return AuthConfig with Google Cloud credentials, or null if unable to get credentials
     */
    private AuthConfig getGoogleCloudAuthConfig() {
        try {
            // Get access token from gcloud
            ProcessBuilder processBuilder = new ProcessBuilder("gcloud", "auth", "print-access-token");
            Process process = processBuilder.start();
            
            StringBuilder tokenBuilder = new StringBuilder();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    tokenBuilder.append(line.trim());
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warn("Failed to get Google Cloud access token, exit code: {}", exitCode);
                return null;
            }
            
            String accessToken = tokenBuilder.toString().trim();
            if (accessToken.isEmpty()) {
                logger.warn("Empty access token received from gcloud");
                return null;
            }
            
            // Create AuthConfig for Google Cloud
            AuthConfig authConfig = new AuthConfig()
                    .withUsername("oauth2accesstoken")
                    .withPassword(accessToken);
            
            logger.debug("Successfully created Google Cloud AuthConfig");
            return authConfig;
            
        } catch (Exception e) {
            logger.warn("Failed to get Google Cloud credentials: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Push Docker image using Docker Java API
     */
    private boolean pushWithDockerJavaAPI(String imageTag) throws Exception {
        // Parse image name and tag
        String[] parts = imageTag.split(":");
        String imageName = parts[0];
        String tag = parts.length > 1 ? parts[1] : "latest";
        
        PushImageCmd pushCmd = dockerClient.pushImageCmd(imageName).withTag(tag);
        
        // Add authentication for Google Cloud Artifact Registry
        if (imageName.contains("pkg.dev") || imageName.contains("gcr.io")) {
            AuthConfig authConfig = getGoogleCloudAuthConfig();
            if (authConfig != null) {
                pushCmd.withAuthConfig(authConfig);
                logger.debug("Added Google Cloud authentication to Docker push command");
            }
        }
        
        // Use CountDownLatch to track completion manually
        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicBoolean hasError = new AtomicBoolean(false);
        
        // Execute the push command with callback
        ResultCallback.Adapter<PushResponseItem> callback = new ResultCallback.Adapter<PushResponseItem>() {
            @Override
            public void onNext(PushResponseItem item) {
                if (item.getStatus() != null) {
                    logger.info("Docker push status: {}", item.getStatus());
                }
                if (item.getErrorDetail() != null) {
                    logger.error("Docker push error: {}", item.getErrorDetail().getMessage());
                    hasError.set(true);
                }
            }
            
            @Override
            public void onError(Throwable throwable) {
                logger.error("Docker push failed", throwable);
                hasError.set(true);
                completionLatch.countDown(); // Signal completion even on error
            }
            
            @Override
            public void onComplete() {
                logger.info("Docker push completed");
                completionLatch.countDown(); // Signal completion
            }
        };
        
        // Start the push operation
        pushCmd.exec(callback);
        
        // Wait for completion with timeout
        boolean completed = completionLatch.await(10, TimeUnit.MINUTES);
        
        if (!completed) {
            logger.warn("Docker push timed out after 10 minutes for image: {}", imageTag);
            throw new RuntimeException("Docker push operation timed out");
        }
        
        if (hasError.get()) {
            throw new RuntimeException("Docker push operation failed with errors");
        }
        
        logger.info("Successfully pushed Docker image via API: {}", imageTag);
        return true;
    }
    
    /**
     * Push Docker image using Docker CLI
     */
    private boolean pushWithDockerCLI(String imageTag) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("docker", "push", imageTag);
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            
            // Read output in real-time
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("Docker push: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            boolean success = (exitCode == 0);
            
            if (success) {
                logger.info("Successfully pushed Docker image via CLI: {}", imageTag);
            } else {
                logger.error("Docker push failed via CLI with exit code: {}", exitCode);
            }
            
            return success;
        } catch (Exception e) {
            logger.error("Docker CLI push failed for image: {}", imageTag, e);
            return false;
        }
    }
    
    /**
     * Check if Docker is available on the system
     * @return true if Docker is available, false otherwise
     */
    public boolean isDockerAvailable() {
        // First try the Docker Java API
        if (dockerClient != null) {
            try {
                dockerClient.pingCmd().exec();
                logger.debug("Docker available via Docker Java API");
                return true;
            } catch (Exception e) {
                logger.debug("Docker Java API ping failed: {}", e.getMessage());
            }
        }
        
        // Fallback to Docker CLI for Unix socket environments (like Colima)
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("docker", "version");
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            boolean isAvailable = (exitCode == 0);
            logger.debug("Docker available via CLI: {}", isAvailable);
            return isAvailable;
        } catch (Exception e) {
            logger.warn("Docker CLI check failed: {}", e.getMessage());
            return false;
        }
    }
} 