package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.UserEmailRequest;
import com.canhlabs.funnyapp.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserEmailRequestRepository extends JpaRepository<UserEmailRequest, UUID> {

    Optional<UserEmailRequest> findByToken(String token);

    boolean existsByEmailAndStatus(String email, Status status);
}