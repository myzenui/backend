package com.armikom.zen.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Component
public class FirebaseTools {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseTools.class);

    private final Firestore firestore;

    public FirebaseTools(Firestore firestore) {
        this.firestore = firestore;
    }

    @Tool(description = "Read plantuml model from datastore")
    public String readModel(@ToolParam(description = "The ID of the document to read") String documentId) {
        logger.info("Reading businessModel from projects/{}", documentId);
        try {
            DocumentReference docRef = firestore.collection("projects").document(documentId);
            Map<String, Object> data = docRef.get().get().getData();
            if (data != null && data.containsKey("businessModel")) {
                String businessModel = (String) data.get("businessModel");
                logger.info("Successfully read businessModel from projects/{}", documentId);
                return businessModel;
            } else {
                logger.error("Field 'businessModel' not found in document projects/{}", documentId);
                throw new RuntimeException("Field 'businessModel' not found in document projects/" + documentId);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error reading businessModel from projects/{}", documentId, e);
            throw new RuntimeException("Error reading businessModel from projects/" + documentId, e);
        }
    }

    @Tool(description = "Write/Update/Save plantuml business model to datastore")
    public String writeModel(@ToolParam(description = "The ID of the document to write to") String documentId,
                             @ToolParam(description = "The content to write to the businessModel field") String content) {
        logger.info("Writing to businessModel in projects/{}", documentId);
        try {
            DocumentReference docRef = firestore.collection("projects").document(documentId);
            Map<String, Object> data = new HashMap<>();
            data.put("businessModel", content);
            docRef.set(data, SetOptions.merge());
            logger.info("Successfully wrote to businessModel in projects/{}", documentId);
            return "Successfully wrote content to businessModel field in projects/" + documentId;
        } catch (Exception e) {
            logger.error("Error writing businessModel to projects/{}", documentId, e);
            throw new RuntimeException("Error writing businessModel to projects/" + documentId, e);
        }
    }
}
