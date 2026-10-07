package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {
    User findAllByUserName(String email);
    User findAllById(Long id);

    @Modifying
    @Query("update User u set u.failedLoginCount = u.failedLoginCount + 1 where u.id = :id")
    int incrementFailedLoginCount(@Param("id") Long id);

    @Modifying
    @Query("update User u set u.failedLoginCount = 0, u.lockedUntil = :until where u.id = :id")
    int lockAccount(@Param("id") Long id, @Param("until") Instant until);

    @Modifying
    @Query("update User u set u.failedLoginCount = 0, u.lockedUntil = null where u.id = :id")
    int clearLoginFailures(@Param("id") Long id);
}
