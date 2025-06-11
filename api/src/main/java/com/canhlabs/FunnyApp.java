package com.canhlabs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties
@EnableAspectJAutoProxy
@EnableCaching
@EnableScheduling
@EnableAsync
@Slf4j
public class FunnyApp implements CommandLineRunner {
    public static void  main(String[] args) {
        SpringApplication.run(FunnyApp.class, args);
        log.info("Starting application");
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("No pre-processing after started");
    }
}