package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.CredentialsVersionCache;
import com.canhlabs.funnyapp.cache.IncognitoCache;
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
    private IncognitoCache incognitoCache;

    @Autowired
    public void injectIncognitoCache(IncognitoCache incognitoCache) {
        this.incognitoCache = incognitoCache;
    }

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
        // Autopilot grants survive CONTRIBUTOR <-> ADMIN moves and are dropped for USER (ADR-0030 BR-7)
        int keptAutopilot = role == UserRole.USER ? 0 : user.getPermissions() & Permission.AUTOPILOT_BITS;
        user.setRole(role);
        user.setPermissions(role.defaultPermissions() | keptAutopilot);
        revokeSessions(user);
        userRepo.save(user);
        credentialsVersionCache.evict(targetId);
        log.info("Role of user id={} set to {} (permissions {}) by user id={}", targetId, role, user.getPermissions(), currentUserId);
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
        incognitoCache.evict(targetId);
    }

    @Override
    public AdminAccountDto updatePermissions(Long targetId, Long currentUserId, int permissions) {
        if (targetId.equals(currentUserId)) {
            throw new IllegalArgumentException("Cannot change your own permissions");
        }
        if ((permissions & ~(Permission.ALL_BITS | Permission.AUTOPILOT_BITS)) != 0) {
            throw new IllegalArgumentException("Unknown permission bits: " + permissions);
        }
        UserRole actorRole = actorRole(currentUserId);
        User user = userRepo.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + targetId));
        requireOutranks(actorRole, user);
        requireAutopilotRules(actorRole, user, permissions);
        user.setPermissions(permissions);
        revokeSessions(user);
        User saved = userRepo.save(user);
        credentialsVersionCache.evict(targetId);
        log.info("Permissions of user id={} set to {} by user id={}", targetId, permissions, currentUserId);
        return toDto(saved != null ? saved : user);
    }

    /** Only the SUPER_ADMIN changes the autopilot bits, and only CONTRIBUTOR/ADMIN accounts may hold them (BR-5, BR-6). */
    private static void requireAutopilotRules(UserRole actorRole, User target, int permissions) {
        int requested = permissions & Permission.AUTOPILOT_BITS;
        if (actorRole != UserRole.SUPER_ADMIN && requested != (target.getPermissions() & Permission.AUTOPILOT_BITS)) {
            throw forbidden("Only the SUPER_ADMIN can change autopilot permissions");
        }
        if (requested != 0 && target.getRole() == UserRole.USER) {
            throw new IllegalArgumentException("Autopilot permissions need the CONTRIBUTOR or ADMIN role");
        }
    }

    /** Role and permissions travel in the JWT: bump credentials_version so older tokens stop working now. */
    private static void revokeSessions(User user) {
        user.setCredentialsVersion(user.getCredentialsVersion() + 1);
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
