package com.armikom.zen.controller;

import com.armikom.zen.service.DockerService;
import com.armikom.zen.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TestController.class)
class TestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DockerService dockerService;

    @MockBean
    private ProjectService projectService;

    @Autowired
    private ObjectMapper objectMapper;

    // Basic endpoint tests
    @Test
    @DisplayName("Should return hello world message")
    void testHelloWorld() throws Exception {
        mockMvc.perform(get("/api/test/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello dear Great World!"));
    }

    @Test
    @DisplayName("Should return health check message")
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/api/test/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Service is up and running!"));
    }

    // Project service tests
    @Test
    @DisplayName("Should successfully create database account with valid parameters")
    void testCreateDatabaseAccountSuccess() throws Exception {
        // Arrange
        when(projectService.createDbAccount("testuser", "TestPassword123!", "testproject"))
                .thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/test/project/create-db-account")
                        .param("userName", "testuser")
                        .param("password", "TestPassword123!")
                        .param("projectName", "testproject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.userName").value("testuser"))
                .andExpect(jsonPath("$.projectName").value("testproject"))
                .andExpect(jsonPath("$.message").value("Database account created successfully"));

        verify(projectService, times(1)).createDbAccount("testuser", "TestPassword123!", "testproject");
    }

    @Test
    @DisplayName("Should return bad request when database account creation fails")
    void testCreateDatabaseAccountFailure() throws Exception {
        // Arrange
        when(projectService.createDbAccount("testuser", "TestPassword123!", "testproject"))
                .thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/test/project/create-db-account")
                        .param("userName", "testuser")
                        .param("password", "TestPassword123!")
                        .param("projectName", "testproject"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.userName").value("testuser"))
                .andExpect(jsonPath("$.projectName").value("testproject"))
                .andExpect(jsonPath("$.message").value("Failed to create database account"));

        verify(projectService, times(1)).createDbAccount("testuser", "TestPassword123!", "testproject");
    }

    @Test
    @DisplayName("Should return internal server error when service throws exception")
    void testCreateDatabaseAccountException() throws Exception {
        // Arrange
        when(projectService.createDbAccount("testuser", "TestPassword123!", "testproject"))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        mockMvc.perform(post("/api/test/project/create-db-account")
                        .param("userName", "testuser")
                        .param("password", "TestPassword123!")
                        .param("projectName", "testproject"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Internal server error: Database connection failed"));

        verify(projectService, times(1)).createDbAccount("testuser", "TestPassword123!", "testproject");
    }

    @Test
    @DisplayName("Should handle missing username parameter")
    void testCreateDatabaseAccountMissingUsername() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/test/project/create-db-account")
                        .param("password", "TestPassword123!")
                        .param("projectName", "testproject"))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).createDbAccount(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle missing password parameter")
    void testCreateDatabaseAccountMissingPassword() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/test/project/create-db-account")
                        .param("userName", "testuser")
                        .param("projectName", "testproject"))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).createDbAccount(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle missing project name parameter")
    void testCreateDatabaseAccountMissingProjectName() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/test/project/create-db-account")
                        .param("userName", "testuser")
                        .param("password", "TestPassword123!"))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).createDbAccount(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle empty parameters")
    void testCreateDatabaseAccountWithEmptyParameters() throws Exception {
        // Arrange
        when(projectService.createDbAccount("", "", ""))
                .thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/test/project/create-db-account")
                        .param("userName", "")
                        .param("password", "")
                        .param("projectName", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(projectService, times(1)).createDbAccount("", "", "");
    }

    @Test
    @DisplayName("Should successfully test database connection when available")
    void testDatabaseConnectionSuccess() throws Exception {
        // Arrange
        when(projectService.testConnection()).thenReturn(true);

        // Act & Assert
        mockMvc.perform(get("/api/test/project/test-connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionWorking").value(true))
                .andExpect(jsonPath("$.message").value("Database connection successful"));

        verify(projectService, times(1)).testConnection();
    }

    @Test
    @DisplayName("Should handle failed database connection test")
    void testDatabaseConnectionFailure() throws Exception {
        // Arrange
        when(projectService.testConnection()).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get("/api/test/project/test-connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionWorking").value(false))
                .andExpect(jsonPath("$.message").value("Database connection failed"));

        verify(projectService, times(1)).testConnection();
    }

    @Test
    @DisplayName("Should handle database connection test exception")
    void testDatabaseConnectionException() throws Exception {
        // Arrange
        when(projectService.testConnection()).thenThrow(new RuntimeException("Connection timeout"));

        // Act & Assert
        mockMvc.perform(get("/api/test/project/test-connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionWorking").value(false))
                .andExpect(jsonPath("$.message").value("Connection test failed: Connection timeout"));

        verify(projectService, times(1)).testConnection();
    }

    // Docker service tests (existing functionality)
    @Test
    @DisplayName("Should successfully push Docker image")
    void testPushDockerImageSuccess() throws Exception {
        // Arrange
        String imageTag = "europe-west1-docker.pkg.dev/myzenui/image/zen-backend:latest";
        when(dockerService.pushDockerImage(imageTag)).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/test/docker/push")
                        .param("imageTag", imageTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.imageTag").value(imageTag))
                .andExpect(jsonPath("$.message").value("Docker image pushed successfully"));

        verify(dockerService, times(1)).pushDockerImage(imageTag);
    }

    @Test
    @DisplayName("Should handle Docker push failure")
    void testPushDockerImageFailure() throws Exception {
        // Arrange
        String imageTag = "europe-west1-docker.pkg.dev/myzenui/image/zen-backend:latest";
        when(dockerService.pushDockerImage(imageTag)).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/api/test/docker/push")
                        .param("imageTag", imageTag))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.imageTag").value(imageTag))
                .andExpect(jsonPath("$.message").value("Docker push failed"));

        verify(dockerService, times(1)).pushDockerImage(imageTag);
    }

    @Test
    @DisplayName("Should get Docker status successfully")
    void testGetDockerStatusSuccess() throws Exception {
        // Arrange
        when(dockerService.isDockerAvailable()).thenReturn(true);

        // Act & Assert
        mockMvc.perform(get("/api/test/docker/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dockerAvailable").value(true))
                .andExpect(jsonPath("$.message").value("Docker is available"));

        verify(dockerService, times(1)).isDockerAvailable();
    }

    @Test
    @DisplayName("Should handle Docker unavailable status")
    void testGetDockerStatusUnavailable() throws Exception {
        // Arrange
        when(dockerService.isDockerAvailable()).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get("/api/test/docker/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dockerAvailable").value(false))
                .andExpect(jsonPath("$.message").value("Docker is not available"));

        verify(dockerService, times(1)).isDockerAvailable();
    }

    @Test
    @DisplayName("Should get Docker debug information")
    void testGetDockerDebugInfo() throws Exception {
        // Arrange
        when(dockerService.isDockerAvailable()).thenReturn(true);

        // Act & Assert
        mockMvc.perform(get("/api/test/docker/debug"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dockerAvailable").value(true))
                .andExpect(jsonPath("$.userHome").exists())
                .andExpect(jsonPath("$.colimaSocketExists").exists())
                .andExpect(jsonPath("$.standardSocketExists").exists())
                .andExpect(jsonPath("$.dockerDesktopSocketExists").exists());

        verify(dockerService, times(1)).isDockerAvailable();
    }

    // Integration test for multiple parameters
    @Test
    @DisplayName("Should handle complex username with valid characters")
    void testCreateDatabaseAccountWithComplexUsername() throws Exception {
        // Arrange
        String complexUsername = "user_123_test";
        when(projectService.createDbAccount(complexUsername, "TestPassword123!", "testproject"))
                .thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/test/project/create-db-account")
                        .param("userName", complexUsername)
                        .param("password", "TestPassword123!")
                        .param("projectName", "testproject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.userName").value(complexUsername));

        verify(projectService, times(1)).createDbAccount(complexUsername, "TestPassword123!", "testproject");
    }

    @Test
    @DisplayName("Should handle complex project name with valid characters")
    void testCreateDatabaseAccountWithComplexProjectName() throws Exception {
        // Arrange
        String complexProjectName = "project_123_test";
        when(projectService.createDbAccount("testuser", "TestPassword123!", complexProjectName))
                .thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/api/test/project/create-db-account")
                        .param("userName", "testuser")
                        .param("password", "TestPassword123!")
                        .param("projectName", complexProjectName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.projectName").value(complexProjectName));

        verify(projectService, times(1)).createDbAccount("testuser", "TestPassword123!", complexProjectName);
    }
} 