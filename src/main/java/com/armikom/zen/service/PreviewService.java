package com.armikom.zen.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.tomcat.util.http.fileupload.FileUtils;

@Service
public class PreviewService {

    private static final Logger logger = LoggerFactory.getLogger(PreviewService.class);

    @Value("${preview.docker.image}")
    private String previewDockerImage;

    private final PlantUmlToCSharpService plantUmlToCSharpService;
    private final DockerService dockerService;

    public PreviewService(PlantUmlToCSharpService plantUmlToCSharpService, DockerService dockerService) {
        this.plantUmlToCSharpService = plantUmlToCSharpService;
        this.dockerService = dockerService;
    }

    /**
     * Generates model files to a preview folder and triggers Docker build
     * @param projectName The name of the project for the preview
     * @param plantUml The PlantUML diagram to generate from
     * @return true if preview generation and build was successful, false otherwise
     */
    public boolean generatePreview(String projectName, String plantUml) {
        if (projectName == null || projectName.trim().isEmpty()) {
            logger.error("Project name cannot be null or empty");
            return false;
        }

        if (plantUml == null || plantUml.trim().isEmpty()) {
            logger.error("PlantUML content cannot be null or empty");
            return false;
        }

        try {
            logger.info("Starting preview generation for project: {}", projectName);

            // Generate model files from PlantUML
            Map<String, String> generatedFiles = plantUmlToCSharpService.generate(plantUml);
            if (generatedFiles.isEmpty()) {
                logger.error("No files generated from PlantUML for project: {}", projectName);
                return false;
            }

            // Create preview folder and write files
            if (!createPreviewFiles(projectName, generatedFiles)) {
                logger.error("Failed to create preview files for project: {}", projectName);
                return false;
            }

            // Trigger Docker build
            if (!buildWithDocker(projectName)) {
                logger.error("Failed to build project with Docker for project: {}", projectName);
                return false;
            }

            logger.info("Preview generation completed successfully for project: {}", projectName);
            return true;

        } catch (Exception e) {
            logger.error("Error during preview generation for project: {}", projectName, e);
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
                FileUtils.deleteDirectory(previewPath.toFile());
                logger.info("Deleted existing preview directory: {}", previewPath);
            }

            // Create the preview directory structure
            Files.createDirectories(previewPath);
            logger.info("Created preview directory: {}", previewPath);

            // Create a basic .csproj file for the project
            createProjectFile(previewPath, projectName);

            // Write all generated model files
            for (Map.Entry<String, String> entry : fileList.entrySet()) {
                File file = new File(previewPath.toFile(), entry.getKey());
                file.getParentFile().mkdirs();
                Files.write(file.toPath(), entry.getValue().getBytes());
                logger.debug("Created preview file: {}", file.getPath());
            }

            logger.info("Successfully created {} preview files for project: {}", fileList.size(), projectName);
            return true;

        } catch (IOException e) {
            logger.error("Error creating preview files for project: {}", projectName, e);
            return false;
        }
    }

    /**
     * Creates a basic .csproj file for the preview project
     */
    private void createProjectFile(Path previewPath, String projectName) throws IOException {
        String csprojContent = """
                <Project Sdk="Microsoft.NET.Sdk">
                
                  <PropertyGroup>
                    <TargetFramework>net8.0</TargetFramework>
                    <ImplicitUsings>enable</ImplicitUsings>
                    <Nullable>enable</Nullable>
                  </PropertyGroup>
                
                  <ItemGroup>
                    <PackageReference Include="DevExpress.Persistent.Base" Version="23.2.3" />
                  </ItemGroup>
                
                </Project>
                """;

        Path csprojPath = previewPath.resolve(projectName + ".csproj");
        Files.write(csprojPath, csprojContent.getBytes());
        logger.debug("Created project file: {}", csprojPath);
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
            logger.info("Building project with Docker image: {} at path: {}", previewDockerImage, previewPath);

            // Build the project using Docker
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", previewPath.toString() + ":/workspace",
                "-w", "/workspace",
                previewDockerImage,
                "dotnet", "build"
            );

            processBuilder.redirectErrorStream(true);
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

            return success;

        } catch (Exception e) {
            logger.error("Error building project with Docker for: {}", projectName, e);
            return false;
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
