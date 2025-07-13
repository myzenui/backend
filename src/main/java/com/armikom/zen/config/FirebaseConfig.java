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

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.credentials.json:}")
    private String firebaseCredentialsJson;

    @Value("${firebase.project.id:}")
    private String firebaseProjectId;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (firebaseCredentialsJson == null || firebaseCredentialsJson.trim().isEmpty()) {
            logger.warn("Firebase credentials are not configured. Firebase will not be initialized.");
            return null;
        }

        FirebaseOptions options;
        try {
            logger.info("Using Firebase credentials from configuration (JSON)");
            ByteArrayInputStream credentialsStream = new ByteArrayInputStream(firebaseCredentialsJson.getBytes());
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);

            FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                    .setCredentials(credentials);

            if (firebaseProjectId != null && !firebaseProjectId.trim().isEmpty()) {
                optionsBuilder.setProjectId(firebaseProjectId);
                logger.info("Using Firebase project ID: {}", firebaseProjectId);
            }

            options = optionsBuilder.build();
        } catch (IOException e) {
            logger.error("Failed to initialize Firebase from credentials", e);
            throw e;
        }

        FirebaseApp app;
        if (FirebaseApp.getApps().isEmpty()) {
            app = FirebaseApp.initializeApp(options);
            logger.info("Firebase initialized successfully for project: {}", firebaseProjectId);
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
        return FirestoreClient.getFirestore(firebaseApp);
    }
}
