// package com.armikom.zen.service;

// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.io.TempDir;
// import org.mockito.Mock;
// import org.mockito.MockitoAnnotations;
// import org.springframework.test.util.ReflectionTestUtils;


// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.util.HashMap;
// import java.util.Map;

// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.Mockito.*;

// class PreviewServiceTest {

//     @Mock
//     private PlantUmlToCSharpService plantUmlToCSharpService;

//     @Mock
//     private DockerService dockerService;

//     private PreviewService previewService;

//     @TempDir
//     Path tempDir;

//     @BeforeEach
//     void setUp() {
//         MockitoAnnotations.openMocks(this);
//         previewService = new PreviewService(plantUmlToCSharpService, dockerService);

//         // Set the preview Docker image property
//         ReflectionTestUtils.setField(previewService, "previewDockerImage", "mcr.microsoft.com/dotnet/sdk:8.0");
//     }

//     @Test
//     @DisplayName("Should successfully generate preview with valid inputs")
//     void testGeneratePreviewSuccess() throws Exception {
//         // Arrange
//         String projectName = "test-project";
//         String plantUml = "@startuml\nclass TestClass {\n  + name: String\n}\n@enduml";

//         Map<String, String> generatedFiles = new HashMap<>();
//         generatedFiles.put("TestClass.cs", "public class TestClass { public string Name { get; set; } }");

//         when(plantUmlToCSharpService.generate(plantUml)).thenReturn(generatedFiles);
//         when(dockerService.isDockerAvailable()).thenReturn(true);



//         // Override the user.home system property to use temp directory
//         String originalUserHome = System.getProperty("user.home");
//         System.setProperty("user.home", tempDir.toString());

//         try {
//             // Act
//             boolean result = previewService.generatePreview(projectName, plantUml);

//             // Assert
//             assertTrue(result, "Preview generation should succeed");
//             verify(plantUmlToCSharpService, times(1)).generate(plantUml);
//             verify(dockerService, times(1)).isDockerAvailable();

//             // Verify files were created
//             Path previewPath = tempDir.resolve("zen").resolve("previews").resolve(projectName);
//             assertTrue(Files.exists(previewPath), "Preview directory should be created");
//             assertTrue(Files.exists(previewPath.resolve(projectName + ".csproj")), "Project file should be created");
//             assertTrue(Files.exists(previewPath.resolve("TestClass.cs")), "Generated file should be created");
//         } finally {
//             // Restore original user.home property
//             System.setProperty("user.home", originalUserHome);
//         }
//     }

//     @Test
//     @DisplayName("Should fail when project name is null")
//     void testGeneratePreviewWithNullProjectName() {
//         // Arrange
//         String plantUml = "@startuml\nclass TestClass {\n  + name: String\n}\n@enduml";

//         // Act
//         boolean result = previewService.generatePreview(null, plantUml);

//         // Assert
//         assertFalse(result, "Preview generation should fail with null project name");
//         verifyNoInteractions(plantUmlToCSharpService);
//         verifyNoInteractions(dockerService);
//     }

//     @Test
//     @DisplayName("Should fail when project name is empty")
//     void testGeneratePreviewWithEmptyProjectName() {
//         // Arrange
//         String plantUml = "@startuml\nclass TestClass {\n  + name: String\n}\n@enduml";

//         // Act
//         boolean result = previewService.generatePreview("", plantUml);

//         // Assert
//         assertFalse(result, "Preview generation should fail with empty project name");
//         verifyNoInteractions(plantUmlToCSharpService);
//         verifyNoInteractions(dockerService);
//     }

//     @Test
//     @DisplayName("Should fail when PlantUML is null")
//     void testGeneratePreviewWithNullPlantUml() {
//         // Arrange
//         String projectName = "test-project";

//         // Act
//         boolean result = previewService.generatePreview(projectName, null);

//         // Assert
//         assertFalse(result, "Preview generation should fail with null PlantUML");
//         verifyNoInteractions(plantUmlToCSharpService);
//         verifyNoInteractions(dockerService);
//     }

//     @Test
//     @DisplayName("Should fail when PlantUML is empty")
//     void testGeneratePreviewWithEmptyPlantUml() {
//         // Arrange
//         String projectName = "test-project";

//         // Act
//         boolean result = previewService.generatePreview(projectName, "");

//         // Assert
//         assertFalse(result, "Preview generation should fail with empty PlantUML");
//         verifyNoInteractions(plantUmlToCSharpService);
//         verifyNoInteractions(dockerService);
//     }

//     @Test
//     @DisplayName("Should fail when no files are generated from PlantUML")
//     void testGeneratePreviewWithNoGeneratedFiles() {
//         // Arrange
//         String projectName = "test-project";
//         String plantUml = "@startuml\nclass TestClass {\n  + name: String\n}\n@enduml";

//         when(plantUmlToCSharpService.generate(plantUml)).thenReturn(new HashMap<>());

//         // Act
//         boolean result = previewService.generatePreview(projectName, plantUml);

//         // Assert
//         assertFalse(result, "Preview generation should fail when no files are generated");
//         verify(plantUmlToCSharpService, times(1)).generate(plantUml);
//         verifyNoInteractions(dockerService);
//     }

//     @Test
//     @DisplayName("Should continue when Docker is not available")
//     void testGeneratePreviewWithDockerUnavailable() throws Exception {
//         // Arrange
//         String projectName = "test-project";
//         String plantUml = "@startuml\nclass TestClass {\n  + name: String\n}\n@enduml";

//         Map<String, String> generatedFiles = new HashMap<>();
//         generatedFiles.put("TestClass.cs", "public class TestClass { public string Name { get; set; } }");

//         when(plantUmlToCSharpService.generate(plantUml)).thenReturn(generatedFiles);
//         when(dockerService.isDockerAvailable()).thenReturn(false);

//         // Act
//         boolean result = previewService.generatePreview(projectName, plantUml);

//         // Assert
//         assertFalse(result, "Preview generation should fail when Docker is not available");
//         verify(plantUmlToCSharpService, times(1)).generate(plantUml);
//         verify(dockerService, times(1)).isDockerAvailable();
//     }

//     @Test
//     @DisplayName("Should successfully cleanup preview directory")
//     void testCleanupPreviewSuccess() throws Exception {
//         // Arrange
//         String projectName = "test-project";

//         // Create a test directory structure
//         Path previewPath = tempDir.resolve("zen").resolve("previews").resolve(projectName);
//         Files.createDirectories(previewPath);
//         Files.createFile(previewPath.resolve("TestClass.cs"));

//         // Override the user.home system property to use temp directory
//         String originalUserHome = System.getProperty("user.home");
//         System.setProperty("user.home", tempDir.toString());

//         try {
//             // Act
//             boolean result = previewService.cleanupPreview(projectName);

//             // Assert
//             assertTrue(result, "Cleanup should succeed");
//             assertFalse(Files.exists(previewPath), "Preview directory should be deleted");
//         } finally {
//             // Restore original user.home property
//             System.setProperty("user.home", originalUserHome);
//         }
//     }

//     @Test
//     @DisplayName("Should successfully cleanup when preview directory does not exist")
//     void testCleanupPreviewWhenDirectoryDoesNotExist() {
//         // Arrange
//         String projectName = "non-existent-project";

//         // Override the user.home system property to use temp directory
//         String originalUserHome = System.getProperty("user.home");
//         System.setProperty("user.home", tempDir.toString());

//         try {
//             // Act
//             boolean result = previewService.cleanupPreview(projectName);

//             // Assert
//             assertTrue(result, "Cleanup should succeed even when directory does not exist");
//         } finally {
//             // Restore original user.home property
//             System.setProperty("user.home", originalUserHome);
//         }
//     }

//     @Test
//     @DisplayName("Should return correct preview location")
//     void testGetPreviewLocation() {
//         // Arrange
//         String projectName = "test-project";
//         String expectedPath = System.getProperty("user.home") + "/zen/previews/" + projectName;

//         // Act
//         String actualPath = previewService.getPreviewLocation(projectName);

//         // Assert
//         assertEquals(expectedPath, actualPath, "Preview location should match expected path");
//     }

//     @Test
//     @DisplayName("Should handle PlantUML service exception gracefully")
//     void testGeneratePreviewWithPlantUmlServiceException() {
//         // Arrange
//         String projectName = "test-project";
//         String plantUml = "@startuml\nclass TestClass {\n  + name: String\n}\n@enduml";

//         when(plantUmlToCSharpService.generate(plantUml)).thenThrow(new RuntimeException("PlantUML service error"));

//         // Act
//         boolean result = previewService.generatePreview(projectName, plantUml);

//         // Assert
//         assertFalse(result, "Preview generation should fail when PlantUML service throws exception");
//         verify(plantUmlToCSharpService, times(1)).generate(plantUml);
//         verifyNoInteractions(dockerService);
//     }

//     @Test
//     @DisplayName("Should create project file with correct content")
//     void testCreateProjectFileContent() throws Exception {
//         // Arrange
//         String projectName = "test-project";
//         String plantUml = "@startuml\nclass TestClass {\n  + name: String\n}\n@enduml";

//         Map<String, String> generatedFiles = new HashMap<>();
//         generatedFiles.put("TestClass.cs", "public class TestClass { public string Name { get; set; } }");

//         when(plantUmlToCSharpService.generate(plantUml)).thenReturn(generatedFiles);
//         when(dockerService.isDockerAvailable()).thenReturn(false); // Set to false to skip actual Docker build

//         // Override the user.home system property to use temp directory
//         String originalUserHome = System.getProperty("user.home");
//         System.setProperty("user.home", tempDir.toString());

//         try {
//             // Act
//             boolean result = previewService.generatePreview(projectName, plantUml);

//             // Assert
//             assertFalse(result, "Preview generation should fail when Docker is not available");

//             // But files should still be created during the process
//             Path previewPath = tempDir.resolve("zen").resolve("previews").resolve(projectName);
//             assertTrue(Files.exists(previewPath), "Preview directory should be created");

//             // Check that .csproj file was created
//             Path csprojPath = previewPath.resolve(projectName + ".csproj");
//             assertTrue(Files.exists(csprojPath), "Project file should be created");

//             // Check content of the project file
//             String csprojContent = Files.readString(csprojPath);
//             assertTrue(csprojContent.contains("<TargetFramework>net8.0</TargetFramework>"),
//                       "Project file should target .NET 8.0");
//             assertTrue(csprojContent.contains("DevExpress.Persistent.Base"),
//                       "Project file should reference DevExpress.Persistent.Base");

//             // Check that the generated C# file was created
//             Path csFilePath = previewPath.resolve("TestClass.cs");
//             assertTrue(Files.exists(csFilePath), "Generated C# file should be created");

//             String csFileContent = Files.readString(csFilePath);
//             assertEquals(generatedFiles.get("TestClass.cs"), csFileContent,
//                         "Generated C# file should have correct content");
//         } finally {
//             // Restore original user.home property
//             System.setProperty("user.home", originalUserHome);
//         }
//     }
// }
