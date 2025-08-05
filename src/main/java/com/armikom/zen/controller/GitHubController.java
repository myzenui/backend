// package com.armikom.zen.controller;

// import com.armikom.zen.service.GitHubService;
// import org.eclipse.jgit.api.errors.GitAPIException;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestBody;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RestController;

// import java.io.IOException;
// import java.util.Map;

// @RestController
// @RequestMapping("/api/github")
// public class GitHubController {

//     @Autowired
//     private GitHubService gitHubService;

//     @PostMapping("/create-repo")
//     public String createRepo(@RequestBody Map<String, String> payload) {
//         String repoName = payload.get("repoName");
//         try {
//             gitHubService.createRepository(repoName);
//             return "Repository " + repoName + " created successfully.";
//         } catch (GitAPIException | IOException e) {
//             e.printStackTrace();
//             return "Error creating repository: " + e.getMessage();
//         }
//     }

//     @PostMapping("/merge-files")
//     public String mergeFiles(@RequestBody Map<String, String> payload) {
//         try {
//             gitHubService.mergeFiles(payload);
//             return "Files merged successfully.";
//         } catch (Exception e) {
//             e.printStackTrace();
//             return "Error merging files: " + e.getMessage();
//         }
//     }
// }
