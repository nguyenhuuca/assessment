package com.canhlabs.funnyapp.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These beans have a public constructor for Spring and a package-private one for tests.
 * Without @Autowired on exactly one of them Spring fails at startup ("No default constructor found"),
 * which unit tests that call the constructors directly cannot see.
 */
class DriveSyncBeansWiringTest {

    @Test
    void multiConstructorBeansMarkExactlyOneConstructorAutowired() {
        for (Class<?> bean : new Class<?>[]{DriveVideoDownloaderImpl.class, DriveSyncServiceImpl.class}) {
            Constructor<?>[] ctors = bean.getDeclaredConstructors();
            long autowired = Arrays.stream(ctors).filter(c -> c.isAnnotationPresent(Autowired.class)).count();
            assertThat(autowired).as(bean.getSimpleName()).isEqualTo(1);
        }
    }
}
