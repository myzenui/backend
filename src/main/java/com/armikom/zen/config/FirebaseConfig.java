package com.armikom.zen.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PreDestroy;

import java.io.IOException;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);
    
    private volatile Firestore firestoreInstance;

    @Value("${firebase.project.id}")
    private String firebaseProjectId;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        logger.info("Initializing Firebase...");

        FirebaseOptions options;
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(firebaseProjectId)
                    .build();
            logger.info("Firebase initialized using Application Default Credentials for project: {}", firebaseProjectId);
        } catch (IOException e) {
            logger.error("Could not get Application Default Credentials. Please configure credentials.", e);
            throw e;
        }

        FirebaseApp app;
        if (FirebaseApp.getApps().isEmpty()) {
            app = FirebaseApp.initializeApp(options);
            logger.info("Firebase App initialized: {}", app.getName());
        } else {
            app = FirebaseApp.getInstance();
        }
        return app;
    }

    @Bean
    public Firestore firestore(FirebaseApp firebaseApp) {
        if (firebaseApp == null) {
            return null;
        }
        
        try {
            firestoreInstance = FirestoreClient.getFirestore(firebaseApp);
            logger.info("Firestore client initialized successfully");
            return firestoreInstance;
        } catch (Exception e) {
            logger.error("Failed to initialize Firestore client", e);
            return null;
        }
    }
    
    @PreDestroy
    public void cleanup() {
        logger.info("Firebase configuration cleanup started");
        
        if (firestoreInstance != null) {
            try {
                firestoreInstance.close();
                logger.info("Firestore client closed successfully");
            } catch (Exception e) {
                logger.warn("Error closing Firestore client: {}", e.getMessage());
            }
        }
        
        try {
            // Close all Firebase apps
            for (FirebaseApp app : FirebaseApp.getApps()) {
                app.delete();
                logger.info("Firebase app deleted: {}", app.getName());
            }
        } catch (Exception e) {
            logger.warn("Error deleting Firebase apps: {}", e.getMessage());
        }
        
        logger.info("Firebase configuration cleanup completed");
    }
}
