package com.armikom.zen.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GitHubService {

    @Value("${github.token}")
    private String githubToken;

    @Value("${github.username}")
    private String githubUsername;

    private final Logger log = LoggerFactory.getLogger(GitHubService.class);

    public void createRepository(String repoName) throws GitAPIException, IOException {
        createRepository(repoName, getLocalPath(repoName));
    }

    public void createRepository(String repoName, Path workingDirectory) throws GitAPIException, IOException {
        // Create a new repository on GitHub (this part requires GitHub API, not JGit)
        // For this example, we assume the repository is already created and empty.

        // delete all files at the directory recursively if exists
        if (Files.exists(workingDirectory)) {
            FileUtils.deleteDirectory(workingDirectory.toFile());
        }

        Git.cloneRepository()
                .setURI("https://github.com/" + githubUsername + "/myzen-" + repoName + ".git")
                .setDirectory(workingDirectory.toFile())
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubToken, ""))
                .call();
        log.info("Repository cloned to: {}", workingDirectory);
    }

    public void mergeFiles(String repoName, Map<String, String> fileList) throws GitAPIException, IOException {
        mergeFiles(repoName, fileList, getLocalPath(repoName));
    }

    public void mergeFiles(String repoName, Map<String, String> fileList, Path workingDirectory) throws GitAPIException, IOException {
        Git git = Git.open(workingDirectory.toFile());

        for (Map.Entry<String, String> entry : fileList.entrySet()) {
            File file = new File(workingDirectory.toFile(), entry.getKey());
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), entry.getValue().getBytes());
            git.add().addFilepattern(entry.getKey()).call();
        }
        // Don't commit here, let pushChanges handle the commit with custom message
    }

    public void pushChanges(String repoName) throws GitAPIException, IOException {
        pushChanges(repoName, "Update files");
    }

    public void pushChanges(String repoName, String commitMessage) throws GitAPIException, IOException {
        pushChanges(repoName, commitMessage, getLocalPath(repoName));
    }

    public void pushChanges(String repoName, String commitMessage, Path workingDirectory) throws GitAPIException, IOException {
        Git git = Git.open(workingDirectory.toFile());
        git.commit().setMessage(commitMessage).call();
        git.push()
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubUsername, githubToken))
                .call();
    }

    public void pushChanges(String repoName, Path workingDirectory) throws GitAPIException, IOException {
        pushChanges(repoName, "Update files", workingDirectory);
    }

    private Path getLocalPath(String repoName) {
        Path localPath = Paths.get(System.getProperty("user.home"), "zen", "git", repoName);
        return localPath;
    }
}
