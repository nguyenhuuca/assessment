package com.canhlabs.funnyapp.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class VirtualThreadMetrics {

    private final MeterRegistry registry;

    public VirtualThreadMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void registerVirtualThreadGauge() {
        registry.gauge("jvm.threads.virtual.count", this, vtm ->
                Thread.getAllStackTraces().keySet().stream()
                        .peek( item ->log.info("registerVirtualThreadGauge: {}", item.getName().concat(item.isVirtual() ? " (virtual)" : " (platform)")))
                        .filter(Thread::isVirtual)
                        .count()
        );
    }
}