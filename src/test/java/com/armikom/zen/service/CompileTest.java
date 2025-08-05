package com.armikom.zen.service;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.Map;

@SpringBootTest
public class CompileTest {
    @Autowired
    private GitHubService gitHubService;
    @Autowired
    private PlantUmlToCSharpService plantUmlToCSharpService;
    @Autowired
    private DockerService dockerService;

    @Test
    public void testCompile() throws GitAPIException, IOException {
        String repoName = "dev";
        gitHubService.createRepository(repoName);
        Map<String, String> generated = plantUmlToCSharpService.generate(Common.model);
        gitHubService.mergeFiles(repoName, generated);
        //gitHubService.pushChanges(repoName);
        //dockerService.buildAndRun("dev", "csharp", "latest");
    }

}
