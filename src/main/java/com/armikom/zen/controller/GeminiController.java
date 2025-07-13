package com.armikom.zen.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gemini")
public class GeminiController {

    private final ChatClient chatClient;

    public GeminiController(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @PostMapping("/chat")
    public String chat(@RequestBody String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    @GetMapping("/hello")
    public String hello(@RequestParam(defaultValue = "Hello from Gemini!") String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
} 