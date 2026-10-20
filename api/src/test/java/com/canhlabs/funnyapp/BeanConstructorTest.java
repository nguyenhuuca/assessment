package com.canhlabs.funnyapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards app startup without a full Spring context: a bean with several constructors must mark one
 * {@code @Autowired}, or Spring falls back to a no-arg constructor that does not exist and the app
 * fails to start (prod outage 2026-10-20, JdkGitHubIssueClient).
 */
class BeanConstructorTest {

    @Test
    void everyComponentHasAnUnambiguousConstructor() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        List<String> ambiguous = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.canhlabs.funnyapp")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            boolean noArg = Arrays.stream(constructors).anyMatch(c -> c.getParameterCount() == 0);
            boolean marked = Arrays.stream(constructors).anyMatch(c -> c.isAnnotationPresent(Autowired.class));
            if (constructors.length > 1 && !noArg && !marked) {
                ambiguous.add(type.getName());
            }
        }

        assertThat(ambiguous).as("beans with several constructors and none @Autowired").isEmpty();
    }
}
