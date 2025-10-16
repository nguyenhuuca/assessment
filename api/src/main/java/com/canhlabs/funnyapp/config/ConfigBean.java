package com.canhlabs.funnyapp.config;

import com.canhlabs.funnyapp.utils.totp.Totp;
import com.canhlabs.funnyapp.utils.totp.TotpImpl;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.List;

@Configuration
public class ConfigBean {
    @Value("${app.google-credential-path}")
    private String credentialPath;

    @Bean
    public PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }

    // initialize bean for rest template
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    // To enable AuthenticationManager injection if needed
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", "funny-app-springboot");
    }


    @Bean
    public Drive googleDrive() throws IOException, GeneralSecurityException {
        // Load credentials from file
        InputStream inputStream;
        if (credentialPath.startsWith("classpath:")) {
            inputStream = getClass().getClassLoader().getResourceAsStream(credentialPath.substring(10));
        } else {
            inputStream = new FileInputStream(credentialPath);
        }
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                inputStream
        ).createScoped(List.of(DriveScopes.DRIVE));

        // Build the Drive client
        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName("VideoStreamApp").build();
    }

    @Bean
    public Totp totp() {
        return new TotpImpl();
    }
}
