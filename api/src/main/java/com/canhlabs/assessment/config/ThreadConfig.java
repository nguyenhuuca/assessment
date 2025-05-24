//package com.canhlabs.assessment.config;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.task.AsyncTaskExecutor;
//import org.springframework.core.task.support.TaskExecutorAdapter;
//import org.springframework.scheduling.annotation.EnableAsync;
//
//import java.util.concurrent.Executors;
//
//// Using in case java = 19
//@EnableAsync
//@Configuration
//@ConditionalOnProperty(
//        value = "app.thread-executor",
//        havingValue = "virtual"
//)
//@Slf4j
//public class ThreadConfig {
//    @Bean
//    public AsyncTaskExecutor applicationTaskExecutor() {
//        log.info("using newVirtualThreadPerTaskExecutor");
//        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
//    }
//
//    @Bean
//    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
//        log.info("tomcat using newVirtualThreadPerTaskExecutor");
//        return protocolHandler -> {
//            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
//        };
//    }
//}