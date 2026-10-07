package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.repo.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginAttemptServiceImplTest {

    @Mock
    private UserRepo userRepo;

    private LoginAttemptServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new LoginAttemptServiceImpl(userRepo);
    }

    @Test
    void recordFailure_belowThreshold_onlyIncrements() {
        when(userRepo.findAllById(1L)).thenReturn(User.builder().id(1L).failedLoginCount(4).build());

        service.recordFailure(1L);

        verify(userRepo).incrementFailedLoginCount(1L);
        verify(userRepo, never()).lockAccount(any(), any());
    }

    @Test
    void recordFailure_fifthFailure_locksFor15MinutesAndResetsCounter() {
        when(userRepo.findAllById(1L)).thenReturn(User.builder().id(1L).failedLoginCount(5).build());
        Instant before = Instant.now();

        service.recordFailure(1L);

        ArgumentCaptor<Instant> until = ArgumentCaptor.forClass(Instant.class);
        verify(userRepo).lockAccount(org.mockito.ArgumentMatchers.eq(1L), until.capture());
        assertThat(until.getValue()).isBetween(before.plus(Duration.ofMinutes(15)),
                Instant.now().plus(Duration.ofMinutes(15)));
    }

    @Test
    void recordSuccess_clearsCounterAndLock() {
        service.recordSuccess(1L);

        verify(userRepo).clearLoginFailures(1L);
    }

    @Test
    void counters_arePersistedInTheirOwnTransaction_soThrown401DoesNotRollThemBack() throws NoSuchMethodException {
        for (String name : new String[]{"recordFailure", "recordSuccess"}) {
            Transactional tx = LoginAttemptServiceImpl.class.getMethod(name, Long.class).getAnnotation(Transactional.class);
            assertThat(tx).as(name).isNotNull();
            assertThat(tx.propagation()).as(name).isEqualTo(Propagation.REQUIRES_NEW);
        }
    }

    @Test
    void passwordLoginService_isNotTransactional() {
        assertThat(PasswordLoginServiceImpl.class.getAnnotation(Transactional.class)).isNull();
        for (var m : PasswordLoginServiceImpl.class.getDeclaredMethods()) {
            assertThat(m.getAnnotation(Transactional.class)).as(m.getName()).isNull();
        }
    }
}
