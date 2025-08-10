package com.armikom.zen.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.tomcat.util.http.fileupload.FileUtils;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;

@Service
public class PreviewService {

    private static final Logger logger = LoggerFactory.getLogger(PreviewService.class);

    @Value("${preview.docker.image}")
    private String previewDockerImage;

    private final PlantUmlToCSharpService plantUmlToCSharpService;
    private final DockerService dockerService;
    private final Firestore firestore;
    private final DatabaseService databaseService;
    private final CloudflareService cloudflareService;

    public PreviewService(
            PlantUmlToCSharpService plantUmlToCSharpService,
            DockerService dockerService,
            Firestore firestore,
            DatabaseService databaseService,
            CloudflareService cloudflareService) {
        this.plantUmlToCSharpService = plantUmlToCSharpService;
        this.dockerService = dockerService;
        this.firestore = firestore;
        this.databaseService = databaseService;
        this.cloudflareService = cloudflareService;
    }

    /**
     * Generates model files to a preview folder and triggers Docker build
     * @param projectName The name of the project for the preview
     * @param plantUml The PlantUML diagram to generate from
     * @return true if preview generation and build was successful, false otherwise
     */
    public boolean generatePreview(String firestoreDocumentId, String plantUml) {
        if (firestoreDocumentId == null || firestoreDocumentId.trim().isEmpty()) {
            logger.error("Firestore document id cannot be null or empty");
            return false;
        }

        if (plantUml == null || plantUml.trim().isEmpty()) {
            logger.error("PlantUML content cannot be null or empty");
            return false;
        }

        try {
            // Extract project id from firestore document field `id`
            String projectId = extractProjectIdFromFirestore(firestoreDocumentId);
            if (projectId == null || projectId.trim().isEmpty()) {
                logger.error("Project id not found in Firestore document: {}", firestoreDocumentId);
                return false;
            }

            logger.info("Starting preview generation for projectId: {} (doc: {})", projectId, firestoreDocumentId);

            // Generate model files from PlantUML
            Map<String, String> generatedFiles = plantUmlToCSharpService.generate(plantUml);
            if (generatedFiles.isEmpty()) {
                logger.error("No files generated from PlantUML for project: {}", projectId);
                return false;
            }

            // Create preview folder and write files
            if (!createPreviewFiles(projectId, generatedFiles)) {
                logger.error("Failed to create preview files for project: {}", projectId);
                return false;
            }

            // Trigger Docker build
            if (!buildWithDocker(projectId)) {
                logger.error("Failed to build project with Docker for project: {}", projectId);
                return false;
            }

            // Build docker image for the generated project and tag as myzen/<projectId>
            if (!buildProjectImage(projectId)) {
                logger.error("Failed to build docker image for project: {}", projectId);
                return false;
            }

            // Create (or ensure) database for the project using projectId for db/user/password
            try {
                boolean dbOk = databaseService.createDatabase(projectId, projectId, projectId);
                if (!dbOk) {
                    logger.error("Failed to create database for project: {}", projectId);
                    return false;
                }
                logger.info("Database created/ensured for project: {}", projectId);
            } catch (Exception dbEx) {
                logger.error("Database creation failed for project: {}", projectId, dbEx);
                return false;
            }

            // Replace existing container (if any) and run a new one on `myzen` network
            replaceAndRunContainer(projectId);

            // Configure Cloudflare: myzen-<projectId>.armikom.com -> http://<projectId>:5000
            try {
                String dnsName = "myzen-" + projectId + ".armikom.com"; // using subdomain style
                String containerName = "myzen-" + projectId;            // container hostname on docker network
                var cfResp = cloudflareService.createCompleteRoute(dnsName, 5000, "http", null, containerName);
                if (!cfResp.isSuccess()) {
                    logger.warn("Cloudflare route setup reported failure: {}", cfResp.getMessage());
                } else {
                    logger.info("Cloudflare route configured for {} -> http://{}:5000", dnsName, containerName);
                }
            } catch (Exception e) {
                logger.warn("Failed to configure Cloudflare route for project {}: {}", projectId, e.getMessage());
            }

            logger.info("Preview generation completed successfully for project: {}", projectId);
            return true;

        } catch (Exception e) {
            logger.error("Error during preview generation", e);
            return false;
        }
    }

    /**
     * Creates preview files in a local folder
     * @param projectName The project name
     * @param fileList Map of filename to file content
     * @return true if successful, false otherwise
     */
    private boolean createPreviewFiles(String projectName, Map<String, String> fileList) {
        try {
            Path previewPath = getPreviewPath(projectName);

            // Delete existing preview directory if it exists
            if (Files.exists(previewPath)) {
                try {
                    ProcessBuilder processBuilder = new ProcessBuilder("sudo", "rm", "-rf", previewPath.toString());
                    Process process = processBuilder.start();
                    int exitCode = process.waitFor();
                    
                    if (exitCode == 0) {
                        logger.info("Deleted existing preview directory using sudo: {}", previewPath);
                    } else {
                        logger.error("Failed to delete preview directory using sudo. Exit code: {}", exitCode);
                        return false;
                    }
                } catch (IOException | InterruptedException e) {
                    logger.error("Error executing sudo rm command: ", e);
                    return false;
                }
            }

            // Create the preview directory structure
            Files.createDirectories(previewPath);
            logger.info("Created preview directory: {}", previewPath);

            // Create a basic .csproj file for the project
            createProjectFile(previewPath);
            
                        // Create nuget.config file
            createNugetConfigFile(previewPath);
            
            // Create Model directory first
            Path modelPath = previewPath.resolve("Model");
            Files.createDirectories(modelPath);
            logger.debug("Created Model directory: {}", modelPath);
            
            // Create BaseEntity.cs file in Model directory
            createBaseEntityFile(modelPath);

            // Write all generated model files
            for (Map.Entry<String, String> entry : fileList.entrySet()) {
                String fileName = entry.getKey();
                String fileContent = entry.getValue();
                
                // Determine the correct path for the file
                Path targetPath;
                if (fileName.endsWith(".cs")) {
                    // Place all C# model classes in the Model subdirectory
                    targetPath = modelPath.resolve(fileName);
                } else {
                    // Place non-C# files (like project files) in the root
                    targetPath = previewPath.resolve(fileName);
                }
                
                // Ensure parent directories exist
                targetPath.getParent().toFile().mkdirs();
                Files.write(targetPath, fileContent.getBytes());
                logger.debug("Created preview file: {}", targetPath);
            }

            logger.info("Successfully created {} preview files for project: {}", fileList.size(), projectName);
            return true;

        } catch (IOException e) {
            logger.error("Error creating preview files for project: {}", projectName, e);
            return false;
        }
    }

    /**
     * Extracts the project id value from Firestore project document's `id` field.
     * Falls back to Firestore document id if the field is absent.
     */
    private String extractProjectIdFromFirestore(String firestoreDocumentId) {
        try {
            DocumentReference ref = firestore.collection("projects").document(firestoreDocumentId);
            DocumentSnapshot snap = ref.get().get();
            if (snap.exists()) {
                Object value = snap.get("id");
                if (value != null) {
                    return String.valueOf(value).trim();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to read project id from Firestore for doc {}: {}", firestoreDocumentId, e.getMessage());
        }
        // Fallback to the provided id
        return firestoreDocumentId;
    }

    /**
     * Creates a basic .csproj file for the preview project
     */
    private void createProjectFile(Path previewPath) throws IOException {
        String csprojContent = """
<Project Sdk="Microsoft.NET.Sdk">

  <PropertyGroup>
    <TargetFramework>net8.0</TargetFramework>
    <ImplicitUsings>enable</ImplicitUsings>
    <Nullable>enable</Nullable>
    <RootNamespace>Zen</RootNamespace>
  </PropertyGroup>

  <ItemGroup>
    <PackageReference Include="DevExpress.ExpressApp" Version="24.2.6" />
    <PackageReference Include="Microsoft.EntityFrameworkCore" Version="8.0.11" />
  </ItemGroup>

</Project>
                """;

        Path csprojPath = previewPath.resolve("Zen.csproj");
        Files.write(csprojPath, csprojContent.getBytes());
        logger.debug("Created project file: {}", csprojPath);
    }

    /**
     * Creates a nuget.config file for the preview project
     */
    private void createNugetConfigFile(Path previewPath) throws IOException {
        String nugetConfigContent = """
<?xml version="1.0" encoding="utf-8"?>
<configuration>
    <packageSources>
        <add key="nuget.org" value="https://api.nuget.org/v3/index.json" protocolVersion="3" />
        <add key="DevExpress" value="https://nuget.devexpress.com/ob5Z9grQGl2RPcDZ4VHeqxccgnHEYwCAAFMUEhJWs236XLluiw/api/v3/index.json" />
    </packageSources>
</configuration>
                """;

        Path nugetConfigPath = previewPath.resolve("nuget.config");
        Files.write(nugetConfigPath, nugetConfigContent.getBytes());
        logger.debug("Created nuget.config file: {}", nugetConfigPath);
    }

    /**
     * Creates a BaseEntity.cs file for the preview project
     */
    private void createBaseEntityFile(Path modelPath) throws IOException {
        String baseEntityContent = """
using DevExpress.ExpressApp;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Zen.Model
{
    public abstract class BaseEntity : IXafEntityObject
    {
        public virtual int Id { get; set; }
        public virtual void OnCreated() { }
        public virtual void OnSaving() { }
        public virtual void OnLoaded() { }
    }
}
                """;

        Path baseEntityPath = modelPath.resolve("BaseEntity.cs");
        Files.write(baseEntityPath, baseEntityContent.getBytes());
        logger.debug("Created BaseEntity.cs file: {}", baseEntityPath);
    }

    /**
     * Builds the project using Docker
     * @param projectName The project name
     * @return true if build was successful, false otherwise
     */
    private boolean buildWithDocker(String projectName) {
        if (!dockerService.isDockerAvailable()) {
            logger.warn("Docker is not available, skipping build for project: {}", projectName);
            return false;
        }

        try {
            Path previewPath = getPreviewPath(projectName);
            logger.info("Building project with Docker image: {} at path: {}", "myzen/devcontainer", previewPath);

            // Build the project using Docker
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", previewPath.toString() + ":/workspace",
                "-w", "/workspace",
                "--entrypoint", "",
                previewDockerImage,
                "dotnet", "build", "Zen.csproj"
            );

            processBuilder.redirectErrorStream(true);
            // log the command
            logger.info("Running command: {}", processBuilder.command());
            logger.info("Preview path: {}", previewPath);
            Process process = processBuilder.start();

            // Read and log output in real-time
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("Docker build: {}", line);
                }
            }

            int exitCode = process.waitFor();
            boolean success = (exitCode == 0);

            if (success) {
                logger.info("Successfully built project with Docker for: {}", projectName);
            } else {
                logger.error("Docker build failed with exit code: {} for project: {}", exitCode, projectName);
            }

            // if success, chown all files in the preview path to the current user
            if (success) {
                try {
                    String currentUser = System.getProperty("user.name");
                    ProcessBuilder chownProcess = new ProcessBuilder("sudo", "chown", "-R", currentUser + ":" + currentUser, previewPath.toString());
                    chownProcess.start().waitFor();
                } catch (Exception chownException) {
                    logger.warn("Failed to change ownership of preview files: {}", chownException.getMessage());
                }
            }

            return success;

        } catch (Exception e) {
            logger.error("Error building project with Docker for: {}", projectName, e);
            return false;
        }
    }

    /**
     * Builds a runnable Docker image for the generated preview and tags it as myzen/<projectId>.
     * If a Dockerfile is not present in the preview directory, will attempt to tag the
     * configured preview image as a fallback so the container lifecycle continues.
     */
    private boolean buildProjectImage(String projectId) {
        try {
            Path previewPath = getPreviewPath(projectId);           // .../zen/previews/<projectId>
            Path parentPath = previewPath.getParent();              // .../zen/previews

            // Ensure Dockerfile exists in parent directory as requested by user
            ensureParentDockerfile(parentPath);

            // Build from the parent directory, using context=<projectId> and -f Dockerfile
            // Tags: <projectId> and myzen/<projectId>
            ProcessBuilder build = new ProcessBuilder(
                    "docker", "build",
                    projectId,
                    "-f", "Dockerfile",
                    "-t", "myzen/" + projectId
            );
            build.directory(parentPath.toFile());
            build.redirectErrorStream(true);
            logger.info("Building image from {} with Dockerfile at {}. Tags: {}, {}",
                    parentPath, parentPath.resolve("Dockerfile"), projectId, "myzen/" + projectId);

            Process process = build.start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("docker build: {}", line);
                }
            }
            int exit = process.waitFor();
            if (exit != 0) {
                logger.error("docker build failed with exit code {}", exit);
                return false;
            }

            logger.info("Docker image built successfully: {} and {}", projectId, "myzen/" + projectId);
            return true;
        } catch (Exception e) {
            logger.error("Error building docker image for project {}", projectId, e);
            return false;
        }
    }

    private void ensureParentDockerfile(Path parentPath) throws IOException {
        Path dockerfilePath = parentPath.resolve("Dockerfile");
        if (Files.exists(dockerfilePath)) {
            return;
        }
        String dockerfile = """
        FROM myzen/devcontainer:6
        WORKDIR /workspace
        COPY nuget.config .
        COPY Zen.csproj .
        RUN dotnet restore
        COPY . .
        RUN dotnet publish Zen.csproj -o /app
        WORKDIR /app
        """;
        Files.writeString(dockerfilePath, dockerfile);
        logger.info("Created Dockerfile at {}", dockerfilePath);
    }

    /**
     * Stops and removes any existing container with the given project id name, then runs a new one
     * connected to `myzen` network with required environment variables.
     */
    private void replaceAndRunContainer(String projectId) {
        String containerName = "myzen-" + projectId;
        String imageTag = "myzen/" + projectId;
        String connectionString = String.format(
                "Server=mysql;Database=%s;User=%s;Password=%s;",
                projectId, projectId, projectId);

        // Stop and remove existing container if exists
        try {
            new ProcessBuilder("docker", "rm", "-f", containerName)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
            logger.info("Ensured old container {} is removed", containerName);
        } catch (Exception e) {
            logger.warn("Failed to remove existing container {}: {}", containerName, e.getMessage());
        }

        // Run new container
        try {
            ProcessBuilder run = new ProcessBuilder(
                    "docker", "run", "-d",
                    "--name", containerName,
                    "--network", "myzen",
                    "-e", "ConnectionStrings__ConnectionString=" + connectionString,
                    imageTag
            );
            run.redirectErrorStream(true);
            logger.info("Starting container {} from image {} on network myzen", containerName, imageTag);
            Process proc = run.start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("docker run: {}", line);
                }
            }
            int exit = proc.waitFor();
            if (exit != 0) {
                logger.error("docker run failed with exit code {} for container {}", exit, containerName);
            }
        } catch (Exception e) {
            logger.error("Failed to run container for project {}", projectId, e);
        }
    }

    /**
     * Gets the local preview path for a project
     * @param projectName The project name
     * @return Path to the preview directory
     */
    private Path getPreviewPath(String projectName) {
        return Paths.get(System.getProperty("user.home"), "zen", "previews", projectName);
    }

    /**
     * Cleans up preview files for a project
     * @param projectName The project name
     * @return true if cleanup was successful, false otherwise
     */
    public boolean cleanupPreview(String projectName) {
        try {
            Path previewPath = getPreviewPath(projectName);
            if (Files.exists(previewPath)) {
                FileUtils.deleteDirectory(previewPath.toFile());
                logger.info("Cleaned up preview directory for project: {}", projectName);
                return true;
            } else {
                logger.info("Preview directory does not exist for project: {}", projectName);
                return true;
            }
        } catch (IOException e) {
            logger.error("Error cleaning up preview for project: {}", projectName, e);
            return false;
        }
    }

    /**
     * Gets the preview path for external access
     * @param projectName The project name
     * @return String representation of the preview path
     */
    public String getPreviewLocation(String projectName) {
        return getPreviewPath(projectName).toString();
    }
}
