package com.canhlabs.funnyapp.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

@Component
@Slf4j
public class ThreadMetrics {

    private final MeterRegistry registry;

    public ThreadMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void init() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        // Gauge: Total thread count
        registry.gauge("jvm.threads.total", threadMXBean, ThreadMXBean::getThreadCount);

        // Gauge: Total virtual thread
        registry.gauge("jvm.threads.virtual.count", this, self ->
                Thread.getAllStackTraces().keySet().stream()
                        .filter(Thread::isVirtual)
                        .count()
        );

        // Gauge: Total platform thread count
        registry.gauge("jvm.threads.platform.count", this, self ->
                Thread.getAllStackTraces().keySet().stream()
                        .filter(t -> !t.isVirtual())
                        .count()
        );
    }
}