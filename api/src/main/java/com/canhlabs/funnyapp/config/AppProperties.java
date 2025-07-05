package com.canhlabs.funnyapp.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Using to load all the properties when start application
 * Can autowire this class in class that register to Spring Application Context
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties("app")
@Slf4j
public class AppProperties {

    private Long tokenExpired;
    private String jwtSecretKey;
    private String googleApiKey;
    // query condition
    private String googlePart;
    private String gptKey;
    private boolean usePasswordless;
    private String domain;
    private String inviteTemplate;
    private String chatGptUrl;
    private String youtubeUrl;
    private String googleCredentialPath;
    private String imageUrl;
    private String mainDomain;
    private String imageStoragePath;



}

