package com.canhlabs.assessment.share;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

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



}

