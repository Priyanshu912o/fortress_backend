package com.fortress.chat.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.util.Date;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.project-id}")
    private String projectId;

    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    @PostConstruct
    public void init() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("FirebaseApp already initialized");
            return;
        }

        try {
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setProjectId(projectId);

            if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
                log.info("Initializing Firebase with service account: {}", serviceAccountPath);
                builder.setCredentials(GoogleCredentials.fromStream(
                        new FileInputStream(serviceAccountPath)));
            } else {
                log.info("Initializing Firebase with Application Default Credentials for project: {}", projectId);
                builder.setCredentials(GoogleCredentials.getApplicationDefault());
            }

            FirebaseApp.initializeApp(builder.build());
            log.info("FirebaseApp initialized successfully");
        } catch (Exception e) {
            log.warn("Could not initialize Firebase with real credentials: {}. " +
                     "Falling back to mock credentials for development/testing mode.", e.getMessage());
            try {
                FirebaseOptions fallbackOptions = FirebaseOptions.builder()
                        .setProjectId(projectId)
                        .setCredentials(GoogleCredentials.create(
                                new AccessToken("mock-dev-token", new Date(System.currentTimeMillis() + 86400000000L))))
                        .build();
                FirebaseApp.initializeApp(fallbackOptions);
                log.info("FirebaseApp initialized with mock credentials for local development");
            } catch (Exception ex) {
                log.error("Failed to initialize Firebase fallback: {}", ex.getMessage());
            }
        }
    }
}
