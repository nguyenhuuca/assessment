package com.canhlabs;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

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

    @KafkaListener(id = "myId", topics = "topic1")
    public void listen(String in){
        log.info(in);
    }


}