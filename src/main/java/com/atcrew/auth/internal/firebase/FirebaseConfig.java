package com.atcrew.auth.internal.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@ConditionalOnExpression("'${firebase.credentials-path:}'.trim().length() > 0")
class FirebaseConfig {

    @Value("${firebase.credentials-path}")
    private String credentialsPath;

    @Bean
    FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        InputStream source = credentialsPath.startsWith("classpath:")
                ? new ClassPathResource(credentialsPath.substring(10)).getInputStream()
                : new FileInputStream(credentialsPath);
        try (InputStream stream = source) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build();
            return FirebaseApp.initializeApp(options);
        }
    }

    @Bean
    FirebaseVerifier firebaseVerifier(FirebaseApp app) {
        return new FirebaseVerifierImpl(FirebaseAuth.getInstance(app));
    }
}
