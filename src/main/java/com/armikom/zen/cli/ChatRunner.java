package com.armikom.zen.cli;

import com.armikom.zen.service.ModelAIService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Scanner;
import java.util.UUID;

@Profile("chat")
@Component
public class ChatRunner implements CommandLineRunner {

    private final ModelAIService modelAIService;

    public ChatRunner(ModelAIService modelAIService) {
        this.modelAIService = modelAIService;
    }

    @Override
    public void run(String... args) throws Exception {
        String conversationId = UUID.randomUUID().toString();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Prompt: ");
            String prompt = scanner.nextLine();
            if ("exit".equalsIgnoreCase(prompt)) {
                break;
            }
            String response = modelAIService.chat(conversationId, "docId", prompt);
            System.out.println("Response: " + response);
        }
    }
}
