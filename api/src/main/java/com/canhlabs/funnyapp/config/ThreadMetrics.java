package com.canhlabs.funnyapp.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.java21.instrument.binder.jdk.VirtualThreadMetrics;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ThreadMetrics {

    private final MeterRegistry registry;

    public ThreadMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void bindVirtualThreadMetrics() {
        new VirtualThreadMetrics().bindTo(registry);
    }
}