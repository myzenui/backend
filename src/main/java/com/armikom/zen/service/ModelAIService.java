package com.armikom.zen.service;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;


@Service
public class ModelAIService {

    private static final Logger logger = LoggerFactory.getLogger(ModelAIService.class);

    private final ChatModel chatModel;
    private final FirebaseTools firebaseTools;
    private final MessageWindowChatMemory chatMemory;
    private final Firestore firestore;

    public ModelAIService(ChatModel chatModel, FirebaseTools firebaseTools,
                          ChatMemoryRepository chatMemoryRepository, Firestore firestore) {
        this.chatModel = chatModel;
        this.firebaseTools = firebaseTools;
        this.firestore = firestore;
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(100)
                .build();
    }

    private List<Message> loadChatHistoryFromFirestore(String conversationId) {
        logger.info("Loading chat history from Firestore for conversationId: {}", conversationId);
        List<Message> messages = new ArrayList<>();

        try {
            QuerySnapshot querySnapshot = firestore
                    .collection("conversations")
                    .document(conversationId)
                    .collection("messages")
                    .orderBy("timestamp")
                    .get()
                    .get();

            for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                Map<String, Object> data = document.getData();
                String text = (String) data.get("text");

                if (text == null || text.trim().isEmpty()) {
                    continue; // Skip empty messages
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> author = (Map<String, Object>) data.get("author");
                if (author != null) {
                    Boolean isUser = (Boolean) author.get("isUser");
                    if (isUser != null && isUser) {
                        messages.add(new UserMessage(text));
                    } else {
                        messages.add(new AssistantMessage(text));
                    }
                }
            }

            logger.info("Loaded {} messages from Firestore for conversationId: {}", messages.size(), conversationId);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error loading chat history from Firestore for conversationId: {}", conversationId, e);
        }

        return messages;
    }

    public String chat(String conversationId, String documentId, String message) {
        logger.info("Chat request for conversationId: {}", conversationId);
        logger.info("Chat request message {}", message);

        // Check if chat memory is empty and load from Firestore if needed
        List<Message> existingMessages = chatMemory.get(conversationId);
        if (existingMessages.isEmpty()) {
            logger.info("Chat memory is empty, loading history from Firestore for conversationId: {}", conversationId);
            List<Message> firestoreMessages = loadChatHistoryFromFirestore(conversationId);
            if (!firestoreMessages.isEmpty()) {
                chatMemory.add(conversationId, firestoreMessages);
                logger.info("Initialized chat memory with {} messages from Firestore", firestoreMessages.size());
            }
        }

        // clean empty messages
        List<Message> messages = chatMemory.get(conversationId);
        List<Message> cleanedMessages =  new ArrayList<>();
        for (Message m : messages) {
            if (!m.getText().isEmpty()) cleanedMessages.add(m);
        }
        chatMemory.clear(conversationId);
        chatMemory.add(conversationId, cleanedMessages);

        var chatClient = ChatClient.builder(chatModel)
                .defaultTools(firebaseTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        String content = chatClient.prompt(
                        "You are a helpful data model engineer who is responsible to read model, take user request and answer or update model according to user requirements and MUST write the model. User is not experienced at software developer so you can suggest better ways if there is. At start please read model.   DocumentId: "
                                + documentId)
                .user(message)
                .system("You are an expert in PlantUML and software design. Your task is to generate PlantUML class diagrams from project descriptions. Reading and writing model is cheap so don't ask user for confirmation. While modifying model please only use public fields. Use only plantuml class diagram related items. Whenever you modified the model you MUST write it to datastore using tool provided. Do not respond final uml model when answering. Only update (write) the model to datastore.")
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        logger.info("Chat response for conversationId: {}", conversationId);
        return content;
    }

}
