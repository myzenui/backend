package com.armikom.zen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import com.armikom.zen.model.Project;

@Service
public class AIService {

    private static final Logger logger = LoggerFactory.getLogger(AIService.class);

    private final ChatClient chatClient;

    public AIService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    public String generatePlantUmlForProject(Project project) {
        String projectDescription = buildProjectDescription(project);
        return generatePlantUmlDiagram(projectDescription);
    }

    private String buildProjectDescription(Project project) {
        StringBuilder description = new StringBuilder();

        description.append("Project Name: ").append(project.getName()).append("\n");
        description.append("Description: ").append(project.getDescription()).append("\n");

        if (project.getUserStories() != null && !project.getUserStories().isEmpty()) {
            description.append("User Stories:\n");
            for (int i = 0; i < project.getUserStories().size(); i++) {
                description.append("- ").append(project.getUserStories().get(i)).append("\n");
            }
        }

        return description.toString();
    }

    private String generatePlantUmlDiagram(String projectDescription) {
        try {
            String systemInstruction = "You are an expert in PlantUML and software design. Your task is to generate PlantUML class diagrams from project descriptions.";

            String prompt = """
                You will be provided with a project description. Your goal is to create a PlantUML class diagram representing the system described in the project description.

                Follow these steps:

                1. Carefully read and understand the project description.
                2. Identify the key classes in the system. Classes typically represent the main entities or concepts in the project description.
                3. Identify the attributes of each class. Attributes are the properties or data associated with each class.
                4. Identify the relationships between the classes. Relationships can be associations, aggregations, compositions, generalizations (inheritance), or dependencies.
                5. Translate the classes, attributes, and relationships into PlantUML syntax.
                6. Output the PlantUML code directly. Do not include any introductory or explanatory text.

                Here is the project description:
                %s

                Example of PlantUML syntax for class diagrams:

                @startuml
                class ClassName {
                  - attribute1: type
                  - attribute2: type
                  + method1()
                  + method2()
                }

                ClassA -- ClassB : association
                ClassC *-- ClassD : aggregation
                ClassE o-- ClassF : composition
                ClassG <|-- ClassH : generalization
                ClassI ..> ClassJ : dependency
                @enduml
                """.formatted(projectDescription);

            String response = chatClient.prompt()
                .system(systemInstruction)
                .user(prompt)
                .call()
                .content();

            logger.info("Vertex AI response received");
            return response;

        } catch (Exception e) {
            logger.error("Error generating PlantUML diagram", e);
            return null;
        }
    }
}
