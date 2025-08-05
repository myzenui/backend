package com.armikom.zen.service;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
//@TestPropertySource(properties = {
//    "github.token=your-test-token",
//    "github.username=your-test-username"
//})
public class GitHubServiceTest {

    @Autowired
    private GitHubService gitHubService;

    @Test
    public void testCreateRepository() throws GitAPIException, IOException {
        // This test will fail without a real GitHub repository and token.
        gitHubService.createRepository("dev");
    }

    @Test
    public void testDirectoryIsCleanedUp() throws GitAPIException, IOException {
        // This test will fail without a real GitHub repository and token.
        String repoName = "test-cleanup";
        Path localPath = Paths.get(System.getProperty("java.io.tmpdir"), repoName);
        gitHubService.createRepository(repoName);
        assertFalse(Files.exists(localPath), "Directory should be deleted after operation");
    }
}
