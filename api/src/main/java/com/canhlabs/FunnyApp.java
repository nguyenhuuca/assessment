package com.canhlabs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableConfigurationProperties
@EnableAspectJAutoProxy
@EnableCaching
@Slf4j
public class FunnyApp {
    public static void  main(String[] args) {
        SpringApplication.run(FunnyApp.class, args);
        log.info("Starting application");
    }
}