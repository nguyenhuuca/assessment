package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private final UserRepo userRepo;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId) {
        // atomic UPDATE ... SET count = count + 1: no lost updates under concurrent guessing
        userRepo.incrementFailedLoginCount(userId);
        User user = userRepo.findAllById(userId);
        if (user != null && user.getFailedLoginCount() >= MAX_FAILURES) {
            userRepo.lockAccount(userId, Instant.now().plus(Duration.ofMinutes(LOCK_MINUTES)));
            log.warn("Account id={} locked for {} minutes after {} failed password attempts", userId, LOCK_MINUTES, MAX_FAILURES);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long userId) {
        userRepo.clearLoginFailures(userId);
    }
}
