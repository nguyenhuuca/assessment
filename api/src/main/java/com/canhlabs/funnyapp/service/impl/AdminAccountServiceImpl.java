package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.AdminAccountDto;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.service.AdminAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AdminAccountServiceImpl implements AdminAccountService {

    private UserRepo userRepo;

    @Autowired
    public void injectUserRepo(UserRepo userRepo) {
        this.userRepo = userRepo;
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
        User user = userRepo.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + targetId));
        user.setRole(role);
        userRepo.save(user);
    }

    @Override
    public void deleteAccount(Long targetId, Long currentUserId) {
        if (targetId.equals(currentUserId)) {
            throw new IllegalArgumentException("Cannot delete your own account");
        }
        userRepo.deleteById(targetId);
    }

    private AdminAccountDto toDto(User u) {
        return AdminAccountDto.builder()
                .id(u.getId())
                .email(u.getUserName())
                .role(u.getRole())
                .mfaEnabled(u.isMfaEnabled())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
