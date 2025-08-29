package com.armikom.zen.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.tomcat.util.http.fileupload.FileUtils;

import com.armikom.zen.enums.DatabaseEnvironment;
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
    private final GitHubService gitHubService;

    public PreviewService(
            PlantUmlToCSharpService plantUmlToCSharpService,
            DockerService dockerService,
            Firestore firestore,
            DatabaseService databaseService,
            CloudflareService cloudflareService,
            GitHubService gitHubService) {
        this.plantUmlToCSharpService = plantUmlToCSharpService;
        this.dockerService = dockerService;
        this.firestore = firestore;
        this.databaseService = databaseService;
        this.cloudflareService = cloudflareService;
        this.gitHubService = gitHubService;
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

            // Try to checkout project from GitHub if it exists
            Path previewPath = getPreviewPath(projectId);
            boolean projectCheckedOut = checkoutProjectFromGitHub(projectId, previewPath);
            
            // Generate model files from PlantUML
            Map<String, String> generatedFiles = plantUmlToCSharpService.generate(plantUml);
            if (generatedFiles.isEmpty()) {
                logger.error("No files generated from PlantUML for project: {}", projectId);
                return false;
            }

            // Create preview folder and write files
            Set<String> updatedFiles = createPreviewFiles(projectId, generatedFiles, projectCheckedOut);
            if (updatedFiles == null) {
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

            // If model has been changed and preview docker builds succeeded, commit and push changes
            if (projectCheckedOut && !updatedFiles.isEmpty()) {
                try {
                    commitAndPushChanges(projectId, firestoreDocumentId, previewPath, updatedFiles);
                } catch (Exception e) {
                    logger.warn("Failed to commit and push changes for project {}: {}", projectId, e.getMessage());
                    // Don't fail the entire preview generation if git push fails
                }
            } else if (projectCheckedOut) {
                logger.info("No files were updated for project {}, skipping git commit/push", projectId);
            }

            // Create (or ensure) database for the project using projectId for db/user/password
            try {
                boolean dbOk = databaseService.createDatabase(DatabaseEnvironment.PREVIEW,projectId, projectId, generatePassword(projectId));
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

    private String generatePassword(String projectId) {
        UUID uuid = UUID.nameUUIDFromBytes(projectId.getBytes(StandardCharsets.UTF_8));
        String hashedId = String.format("%016x", uuid.getMostSignificantBits());
        return "MyZen25!" + hashedId;
    }

    /**
     * Creates preview files in a local folder
     * @param projectName The project name
     * @param fileList Map of filename to file content
     * @param projectCheckedOut Whether the project was checked out from GitHub
     * @return Set of updated files (relative paths) for git tracking, or null if creation failed
     */
    private Set<String> createPreviewFiles(String projectName, Map<String, String> fileList, boolean projectCheckedOut) {
        try {
            Path previewPath = getPreviewPath(projectName);

            // Only delete existing preview directory if project was not checked out from GitHub
            if (!projectCheckedOut && Files.exists(previewPath)) {
                try {
                    ProcessBuilder processBuilder = new ProcessBuilder("sudo", "rm", "-rf", previewPath.toString());
                    Process process = processBuilder.start();
                    int exitCode = process.waitFor();
                    
                    if (exitCode == 0) {
                        logger.info("Deleted existing preview directory using sudo: {}", previewPath);
                    } else {
                        logger.error("Failed to delete preview directory using sudo. Exit code: {}", exitCode);
                        return null;
                    }
                } catch (IOException | InterruptedException e) {
                    logger.error("Error executing sudo rm command: ", e);
                    return null;
                }
            }

            // Create the preview directory structure if it doesn't exist
            if (!Files.exists(previewPath)) {
                Files.createDirectories(previewPath);
                logger.info("Created preview directory: {}", previewPath);
            }

            // Track which files get updated for git purposes
            Set<String> updatedFiles = new HashSet<>();
            
            // Create basic project files only if project was not checked out from GitHub
            createProjectFile(previewPath, updatedFiles);
            createNugetConfigFile(previewPath, updatedFiles);
            
            // Create Model directory first
            Path modelPath = previewPath.resolve("Model");
            Files.createDirectories(modelPath);
            logger.debug("Created Model directory: {}", modelPath);
            
            // Create BaseEntity.cs file in Model directory
            createBaseEntityFile(modelPath, previewPath, updatedFiles);

            // Create devcontainer.json file in .devcontainer directory
            createDevcontainerFile(previewPath, updatedFiles);

            // Create VSCode configuration files
            createVSCodeLaunchFile(previewPath, updatedFiles);
            createVSCodeTasksFile(previewPath, updatedFiles);

            // Write all generated model files and track updates
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
                
                // Check if file content is different and track if updated
                if (shouldWriteFile(targetPath, fileContent, updatedFiles, previewPath)) {
                    Files.write(targetPath, fileContent.getBytes());
                    logger.debug("Created/updated preview file: {}", targetPath);
                } else {
                    logger.debug("Generated file content unchanged: {}", targetPath);
                }
            }

            logger.info("Successfully created {} preview files for project: {}", fileList.size(), projectName);
            logger.info("Total updated files tracked for git: {}", updatedFiles.size());
            return updatedFiles;

        } catch (IOException e) {
            logger.error("Error creating preview files for project: {}", projectName, e);
            return null;
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
     * Helper method to check if file content needs to be updated and track updated files
     * @param filePath Path to the file to check
     * @param newContent The new content to compare against
     * @param updatedFiles Set to track which files were updated (relative to preview path)
     * @param previewPath The preview path to calculate relative paths
     * @return true if file should be written (doesn't exist or content is different), false otherwise
     */
    private boolean shouldWriteFile(Path filePath, String newContent, Set<String> updatedFiles, Path previewPath) throws IOException {
        boolean shouldWrite = false;
        
        if (!Files.exists(filePath)) {
            shouldWrite = true;
        } else {
            String existingContent = Files.readString(filePath, StandardCharsets.UTF_8);
            shouldWrite = !existingContent.equals(newContent);
        }
        
        // Track the file if it will be updated
        if (shouldWrite && updatedFiles != null) {
            String relativePath = previewPath.relativize(filePath).toString().replace('\\', '/');
            updatedFiles.add(relativePath);
        }
        
        return shouldWrite;
    }

    /**
     * Creates a basic .csproj file for the preview project
     */
    private void createProjectFile(Path previewPath, Set<String> updatedFiles) throws IOException {
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
        
        // Only write file if content is different or file doesn't exist
        if (shouldWriteFile(csprojPath, csprojContent, updatedFiles, previewPath)) {
            Files.write(csprojPath, csprojContent.getBytes());
            logger.debug("Created/updated project file: {}", csprojPath);
        } else {
            logger.debug("Project file content unchanged: {}", csprojPath);
        }
    }

    /**
     * Creates a nuget.config file for the preview project
     */
    private void createNugetConfigFile(Path previewPath, Set<String> updatedFiles) throws IOException {
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
        
        // Only write file if content is different or file doesn't exist
        if (shouldWriteFile(nugetConfigPath, nugetConfigContent, updatedFiles, previewPath)) {
            Files.write(nugetConfigPath, nugetConfigContent.getBytes());
            logger.debug("Created/updated nuget.config file: {}", nugetConfigPath);
        } else {
            logger.debug("Nuget config file content unchanged: {}", nugetConfigPath);
        }
    }

    /**
     * Creates a BaseEntity.cs file for the preview project
     */
    private void createBaseEntityFile(Path modelPath, Path previewPath, Set<String> updatedFiles) throws IOException {
        String baseEntityContent = """
using DevExpress.ExpressApp;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.ComponentModel;
using System.ComponentModel.DataAnnotations;

namespace Zen.Model
{
    public abstract class BaseEntity : IXafEntityObject
    {
        [Key, Browsable(false)]
        public virtual int Id { get; set; }
        public virtual void OnCreated() { }
        public virtual void OnSaving() { }
        public virtual void OnLoaded() { }
    }
}
                """;

        Path baseEntityPath = modelPath.resolve("BaseEntity.cs");
        
        // Only write file if content is different or file doesn't exist
        if (shouldWriteFile(baseEntityPath, baseEntityContent, updatedFiles, previewPath)) {
            Files.write(baseEntityPath, baseEntityContent.getBytes());
            logger.debug("Created/updated BaseEntity.cs file: {}", baseEntityPath);
        } else {
            logger.debug("BaseEntity.cs file content unchanged: {}", baseEntityPath);
        }
    }

    /**
     * Creates a devcontainer.json file for the preview project
     */
    private void createDevcontainerFile(Path previewPath, Set<String> updatedFiles) throws IOException {
        // Create .devcontainer directory if it doesn't exist
        Path devcontainerDir = previewPath.resolve(".devcontainer");
        if (!Files.exists(devcontainerDir)) {
            Files.createDirectories(devcontainerDir);
            logger.debug("Created .devcontainer directory: {}", devcontainerDir);
        }

        String devcontainerContent = """
{
    "image": "myzen/devcontainer:19",
    "postStartCommand": "/bin/bash /start.sh",
    "forwardPorts": [1433],
    "containerEnv": {
        "ACCEPT_EULA": "Y",
        "MSSQL_PID": "Express",
        "MSSQL_SA_PASSWORD": "ZenPassword123!"
    },
    "mounts": ["type=volume,source=dev-mssql-data,target=/var/opt/mssql"], 
    "features": {
        "ghcr.io/joshuanianji/devcontainer-features/gcloud-cli-persistence:1": {}
    },
    "customizations": {
        "vscode": {
            "extensions": [
                "ms-dotnettools.csharp"
            ],
            "settings": {
                "files.exclude": {
                    "**/.classpath": true,
                    "**/.project": true,
                    "**/.settings": true,
                    "**/.factorypath": true,
                    "**/.*": true,
                    "**/bin": true,
                    "**/obj": true,
                    "nuget.config": true,
                    "dev.sln": true,
                    "global.json": true,
                }
            }
        }
    }
}
                """;

        Path devcontainerJsonPath = devcontainerDir.resolve("devcontainer.json");
        
        // Only write file if content is different or file doesn't exist
        if (shouldWriteFile(devcontainerJsonPath, devcontainerContent, updatedFiles, previewPath)) {
            Files.write(devcontainerJsonPath, devcontainerContent.getBytes());
            logger.debug("Created/updated devcontainer.json file: {}", devcontainerJsonPath);
        } else {
            logger.debug("devcontainer.json file content unchanged: {}", devcontainerJsonPath);
        }
    }

    /**
     * Creates a launch.json file for the preview project
     */
    private void createVSCodeLaunchFile(Path previewPath, Set<String> updatedFiles) throws IOException {
        // Create .vscode directory if it doesn't exist
        Path vscodeDir = previewPath.resolve(".vscode");
        if (!Files.exists(vscodeDir)) {
            Files.createDirectories(vscodeDir);
            logger.debug("Created .vscode directory: {}", vscodeDir);
        }

        String launchContent = """
{
  "version": "0.2.0",
  "configurations": [
    {
      "name": "Zen Start",
      "type": "coreclr",
      "request": "launch",
      "preLaunchTask": "build zen app",
      "program": "Zen.Blazor.Server.dll",
      "args": [],
      "stopAtEntry": false,
      "console": "integratedTerminal",
      "cwd": "/app"
    }
  ]
}
                """;

        Path launchJsonPath = vscodeDir.resolve("launch.json");
        
        // Only write file if content is different or file doesn't exist
        if (shouldWriteFile(launchJsonPath, launchContent, updatedFiles, previewPath)) {
            Files.write(launchJsonPath, launchContent.getBytes());
            logger.debug("Created/updated launch.json file: {}", launchJsonPath);
        } else {
            logger.debug("launch.json file content unchanged: {}", launchJsonPath);
        }
    }

    /**
     * Creates a tasks.json file for the preview project
     */
    private void createVSCodeTasksFile(Path previewPath, Set<String> updatedFiles) throws IOException {
        // Create .vscode directory if it doesn't exist
        Path vscodeDir = previewPath.resolve(".vscode");
        if (!Files.exists(vscodeDir)) {
            Files.createDirectories(vscodeDir);
            logger.debug("Created .vscode directory: {}", vscodeDir);
        }

        String tasksContent = """
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "build zen app",
      "command": "dotnet",
      "type": "process",
      "args": [
        "build",
        "${workspaceFolder}/Zen.csproj",
        "/property:GenerateFullPaths=true",
        "/consoleloggerparameters:NoSummary",
        "/p:OutputPath=/app"
      ],
      "problemMatcher": "$msCompile",
      "group": {
        "kind": "build",
        "isDefault": true
      },
      "detail": "Builds Zen project."
    }
  ]
}
                """;

        Path tasksJsonPath = vscodeDir.resolve("tasks.json");
        
        // Only write file if content is different or file doesn't exist
        if (shouldWriteFile(tasksJsonPath, tasksContent, updatedFiles, previewPath)) {
            Files.write(tasksJsonPath, tasksContent.getBytes());
            logger.debug("Created/updated tasks.json file: {}", tasksJsonPath);
        } else {
            logger.debug("tasks.json file content unchanged: {}", tasksJsonPath);
        }
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
        String dockerfile = """
        FROM myzen/devcontainer:19
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
                "Server=mssql_zen,1433;Database=%s;User Id=%s;Password=%s;Encrypt=true;TrustServerCertificate=true;",
                projectId, projectId, generatePassword(projectId));

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

    /**
     * Attempts to checkout a project from GitHub if it exists
     * @param projectId The project identifier
     * @param previewPath
     * @return true if project was successfully checked out, false otherwise
     */
    private boolean checkoutProjectFromGitHub(String projectId, Path previewPath) {
        try {
            gitHubService.createRepository(projectId, previewPath);
            logger.info("Successfully checked out project from GitHub to: {}", previewPath);
            return true;
        } catch (Exception e) {
            logger.info("Project {} does not exist on GitHub or failed to checkout: {}", projectId, e.getMessage());
            return false;
        }
    }

    /**
     * Commits and pushes changes to GitHub repository
     * @param projectId The project identifier
     * @param firestoreDocumentId The Firestore document ID for context
     * @param previewPath
     * @param updatedFiles Set of files that were actually updated (relative paths)
     */
    private void commitAndPushChanges(String projectId, String firestoreDocumentId, Path previewPath, Set<String> updatedFiles) {
        try {
            // Only collect files that were actually updated
            Map<String, String> filesToCommit = new HashMap<>();
            
            logger.info("Committing {} updated files for project {}: {}", updatedFiles.size(), projectId, updatedFiles);
            
            for (String relativePath : updatedFiles) {
                Path filePath = previewPath.resolve(relativePath);
                if (Files.exists(filePath)) {
                    try {
                        String content = Files.readString(filePath);
                        filesToCommit.put(relativePath, content);
                        logger.debug("Added updated file to commit: {}", relativePath);
                    } catch (IOException e) {
                        logger.warn("Failed to read updated file for commit: {}", filePath, e);
                    }
                } else {
                    logger.warn("Updated file no longer exists: {}", filePath);
                }
            }
            
            if (filesToCommit.isEmpty()) {
                logger.info("No valid files to commit for project: {}", projectId);
                return;
            }
            
            // Merge only the updated files and push changes
            gitHubService.mergeFiles(projectId, filesToCommit, previewPath);
            String commitMessage = String.format("Updated %d files for preview at %s", filesToCommit.size(), firestoreDocumentId);
            gitHubService.pushChanges(projectId, commitMessage, previewPath);
            
            logger.info("Successfully committed and pushed {} updated files for project: {} (preview for doc: {})", 
                       filesToCommit.size(), projectId, firestoreDocumentId);
            
        } catch (Exception e) {
            logger.error("Failed to commit and push changes for project: {}", projectId, e);
        }
    }
}
