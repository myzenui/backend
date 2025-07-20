package com.armikom.zen.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentChange;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.ListenerRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FirebaseMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseMessageListener.class);

    private final Firestore firestore;
    private final ModelAIService modelAIService;
    private volatile ListenerRegistration listener;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    public FirebaseMessageListener(Firestore firestore, ModelAIService modelAIService) {
        this.firestore = firestore;
        this.modelAIService = modelAIService;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed() {
        if (initialized.compareAndSet(false, true)) {
            try {
                // Add a small delay to ensure Firestore is fully ready and avoid conflicts with AIService
                Thread.sleep(1500);
                init();
                logger.info("FirebaseMessageListener initialized successfully");
            } catch (Exception e) {
                logger.error("Failed to initialize FirebaseMessageListener", e);
                initialized.set(false);
            }
        }
    }

    private void init() {
        if (shutdownRequested.get()) {
            logger.info("Shutdown requested, skipping message listener initialization");
            return;
        }
        
        logger.info("Initializing Firebase message listener for all conversations...");
        
        try {
            listener = firestore.collectionGroup("messages")
                    .whereEqualTo("answeredAt", null)
                    .whereEqualTo("isUser", true)
                    .addSnapshotListener((snapshots, e) -> {
                        if (shutdownRequested.get()) {
                            logger.debug("Shutdown requested, ignoring message snapshot event");
                            return;
                        }
                        
                        if (e != null) {
                            if (e.getCause() instanceof RejectedExecutionException) {
                                logger.warn("Firestore message listener rejected execution - likely shutting down");
                                return;
                            }
                            logger.error("Listen failed: " + e);
                            return;
                        }

                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            if (shutdownRequested.get()) {
                                logger.debug("Shutdown requested, stopping message processing");
                                return;
                            }
                            
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                handleNewMessage(dc);
                            }
                        }
                    });
        } catch (RejectedExecutionException e) {
            logger.warn("Failed to start message listener due to executor shutdown: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error starting message listener", e);
        }
    }
    
    private void handleNewMessage(DocumentChange dc) {
        if (shutdownRequested.get()) {
            logger.debug("Shutdown requested, skipping message: {}", dc.getDocument().getId());
            return;
        }
        
        try {
            DocumentReference messageRef = dc.getDocument().getReference();
            DocumentReference conversationRef = messageRef.getParent().getParent();
            String conversationId = conversationRef.getId();
            logger.info("New message in conversation {}", conversationId);

            ApiFuture<DocumentSnapshot> conversationFuture = conversationRef.get();
            try {
                DocumentSnapshot conversationDoc = conversationFuture.get();
                if (conversationDoc.exists()) {
                    String projectId = conversationDoc.getString("projectId");
                    if (projectId != null) {
                        Map<String, Object> messageData = dc.getDocument().getData();
                        String authorId = getAuthorId(messageData);

                        if (!"zen".equals(authorId)) {
                            if (messageData.containsKey("text")) {
                                String messageText = (String) messageData.get("text");
                                if (messageText != null && !messageText.isEmpty()) {
                                    if (!shutdownRequested.get()) {
                                        String response = modelAIService.chat(conversationId, projectId, messageText);
                                        saveResponse(conversationId, messageRef, response);
                                    }
                                }
                            } else {
                                logger.warn("Message {} in conversation {} has no text field.", dc.getDocument().getId(), conversationId);
                            }
                        }
                    } else {
                        logger.error("projectId is null for conversation {}", conversationId);
                    }
                } else {
                    logger.error("Conversation document not found for conversation {}", conversationId);
                }
            } catch (InterruptedException | ExecutionException ex) {
                if (ex.getCause() instanceof IllegalStateException && 
                    ex.getCause().getMessage().contains("Firestore client has already been closed")) {
                    logger.warn("Firestore client closed during conversation fetch: {}", conversationId);
                } else {
                    logger.error("Error fetching conversation document for conversation {}", conversationId, ex);
                }
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            logger.error("Error handling new message: {}", dc.getDocument().getId(), e);
        }
    }

    private String getAuthorId(Map<String, Object> messageData) {
        if (messageData.containsKey("author")) {
            Object authorObj = messageData.get("author");
            if (authorObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> authorMap = (Map<String, Object>) authorObj;
                if (authorMap.containsKey("id")) {
                    return (String) authorMap.get("id");
                }
            }
        }
        return null;
    }

    private void saveResponse(String conversationId, DocumentReference messageRef, String responseText) {
        if (shutdownRequested.get()) {
            logger.debug("Shutdown requested, skipping response save for conversation: {}", conversationId);
            return;
        }
        
        try {
            // Save the AI's response
            Map<String, Object> responseMessage = Map.of(
                    "text", responseText,
                    "author", Map.of("id", "zen", "name", "Zen"),
                    "timestamp", Timestamp.now());
            firestore.collection("conversations").document(conversationId).collection("messages").add(responseMessage);
            logger.info("Saved response to conversation {}", conversationId);

            // Mark the user's message as answered
            messageRef.update("answeredAt", Timestamp.now());
            logger.info("Updated message {} with answeredAt timestamp.", messageRef.getId());
        } catch (Exception e) {
            if (e.getCause() instanceof IllegalStateException && 
                e.getCause().getMessage().contains("Firestore client has already been closed")) {
                logger.warn("Firestore client closed during response save for conversation: {}", conversationId);
            } else {
                logger.error("Error saving response for conversation {}: {}", conversationId, e);
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        shutdownRequested.set(true);
        logger.info("FirebaseMessageListener shutdown requested");
        
        if (listener != null) {
            try {
                listener.remove();
                logger.info("Stopped Firebase message listener");
            } catch (Exception e) {
                logger.warn("Error stopping message listener: {}", e.getMessage());
            }
        }
        
        initialized.set(false);
        logger.info("FirebaseMessageListener cleanup completed");
    }
}
