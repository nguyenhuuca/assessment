package com.canhlabs.funnyapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documents the finding behind NotificationEventListener's @Async: with spring.threads.virtual.enabled=true
 * (the app default) Spring Boot's auto-configured applicationTaskExecutor - the executor @EnableAsync uses -
 * runs tasks on virtual threads, so no custom executor bean is needed.
 */
class AsyncExecutorVirtualThreadTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class));

    @Test
    void applicationTaskExecutor_runsOnVirtualThreads_whenVirtualThreadsEnabled() throws Exception {
        runner.withPropertyValues("spring.threads.virtual.enabled=true").run(ctx -> {
            AsyncTaskExecutor executor = ctx.getBean("applicationTaskExecutor", AsyncTaskExecutor.class);
            assertThat(executor).isInstanceOf(SimpleAsyncTaskExecutor.class);
            AtomicBoolean virtual = new AtomicBoolean();
            executor.submit(() -> virtual.set(Thread.currentThread().isVirtual())).get();
            assertThat(virtual).isTrue();
        });
    }
}
