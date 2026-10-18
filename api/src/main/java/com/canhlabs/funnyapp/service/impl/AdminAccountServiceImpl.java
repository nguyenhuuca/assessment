package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.CredentialsVersionCache;
import com.canhlabs.funnyapp.dto.admin.AdminAccountDto;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.service.AdminAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AdminAccountServiceImpl implements AdminAccountService {

    private UserRepo userRepo;
    private CredentialsVersionCache credentialsVersionCache;

    @Autowired
    public void injectUserRepo(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @Autowired
    public void injectCredentialsVersionCache(CredentialsVersionCache credentialsVersionCache) {
        this.credentialsVersionCache = credentialsVersionCache;
    }

    @Override
    public Page<AdminAccountDto> getAccounts(Pageable pageable) {
        return userRepo.findAll(pageable).map(this::toDto);
    }

    @Override
    public void updateRole(Long targetId, Long currentUserId, UserRole role) {
        if (targetId.equals(currentUserId)) {
            throw new IllegalArgumentException("Cannot change your own role");
        }
        UserRole actorRole = actorRole(currentUserId);
        if (!actorRole.outranks(role)) {
            throw forbidden("Can only assign a role lower than your own");
        }
        User user = userRepo.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + targetId));
        requireOutranks(actorRole, user);
        user.setRole(role);
        userRepo.save(user);
    }

    @Override
    public void deleteAccount(Long targetId, Long currentUserId) {
        if (targetId.equals(currentUserId)) {
            throw new IllegalArgumentException("Cannot delete your own account");
        }
        UserRole actorRole = actorRole(currentUserId);
        User user = userRepo.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + targetId));
        requireOutranks(actorRole, user);
        userRepo.deleteById(targetId);
    }

    @Override
    public AdminAccountDto updatePermissions(Long targetId, Long currentUserId, int permissions) {
        if (targetId.equals(currentUserId)) {
            throw new IllegalArgumentException("Cannot change your own permissions");
        }
        if ((permissions & ~Permission.ALL_BITS) != 0) {
            throw new IllegalArgumentException("Unknown permission bits: " + permissions);
        }
        UserRole actorRole = actorRole(currentUserId);
        User user = userRepo.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + targetId));
        requireOutranks(actorRole, user);
        user.setPermissions(permissions);
        // Permissions travel in the JWT: revoke the target's tokens so a removed bit stops working now
        user.setCredentialsVersion(user.getCredentialsVersion() + 1);
        User saved = userRepo.save(user);
        credentialsVersionCache.evict(targetId);
        log.info("Permissions of user id={} set to {} by user id={}", targetId, permissions, currentUserId);
        return toDto(saved != null ? saved : user);
    }

    /** Read from the DB, not the JWT: a token issued before a demotion still carries the old role. */
    private UserRole actorRole(Long currentUserId) {
        return userRepo.findById(currentUserId)
                .map(User::getRole)
                .orElseThrow(() -> forbidden("Current user not found"));
    }

    private static void requireOutranks(UserRole actorRole, User target) {
        if (!actorRole.outranks(target.getRole())) {
            throw forbidden("Can only manage accounts with a role lower than your own");
        }
    }

    private static CustomException forbidden(String message) {
        return CustomException.builder().status(HttpStatus.FORBIDDEN).message(message).build();
    }

    private AdminAccountDto toDto(User u) {
        return AdminAccountDto.builder()
                .id(u.getId())
                .email(u.getUserName())
                .role(u.getRole())
                .mfaEnabled(u.isMfaEnabled())
                .permissions(u.getPermissions())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
