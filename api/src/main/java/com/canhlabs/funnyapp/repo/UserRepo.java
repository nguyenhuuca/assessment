package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {
    User findAllByUserName(String email);
    User findAllById(Long id);

    /** Row lock for entitlement changes (Premium extension) so two payments cannot overwrite each other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    java.util.Optional<User> findByIdForUpdate(@Param("id") Long id);

    /** Admin accounts that can receive admin notifications (not deleted). */
    @Query("select u from User u where u.role in (com.canhlabs.funnyapp.enums.UserRole.ADMIN, com.canhlabs.funnyapp.enums.UserRole.SUPER_ADMIN) and u.deletedAt is null")
    java.util.List<User> findActiveAdmins();

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
