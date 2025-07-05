package com.armikom.zen.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.api.async.ResultCallback;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;

@Service
public class DockerService {
    
    private static final Logger logger = LoggerFactory.getLogger(DockerService.class);
    private DockerClient dockerClient;
    
    @PostConstruct
    public void init() {
        try {
            String dockerHost = determineDockerHost();
            
            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
            
            ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
            
            dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();
            
            logger.info("Docker client initialized successfully using host: {}", dockerHost);
        } catch (Exception e) {
            logger.error("Failed to initialize Docker client", e);
            dockerClient = null;
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
     * Push Docker image using Docker Java API
     */
    private boolean pushWithDockerJavaAPI(String imageTag) throws Exception {
        // Parse image name and tag
        String[] parts = imageTag.split(":");
        String imageName = parts[0];
        String tag = parts.length > 1 ? parts[1] : "latest";
        
        PushImageCmd pushCmd = dockerClient.pushImageCmd(imageName).withTag(tag);
        
        // Execute the push command with callback
        ResultCallback.Adapter<PushResponseItem> callback = new ResultCallback.Adapter<PushResponseItem>() {
            @Override
            public void onNext(PushResponseItem item) {
                if (item.getStatus() != null) {
                    logger.info("Docker push status: {}", item.getStatus());
                }
                if (item.getErrorDetail() != null) {
                    logger.error("Docker push error: {}", item.getErrorDetail().getMessage());
                }
            }
            
            @Override
            public void onError(Throwable throwable) {
                logger.error("Docker push failed", throwable);
            }
            
            @Override
            public void onComplete() {
                logger.info("Docker push completed");
            }
        };
        
        pushCmd.exec(callback).awaitCompletion();
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