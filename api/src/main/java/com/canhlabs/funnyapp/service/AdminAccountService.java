package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.admin.AdminAccountDto;
import com.canhlabs.funnyapp.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminAccountService {
    Page<AdminAccountDto> getAccounts(Pageable pageable);
    /** Fresh read of one account; throws ResourceNotFoundException (a 400 on single-row endpoints) when missing. */
    AdminAccountDto getAccount(Long id);
    void updateRole(Long targetId, Long currentUserId, UserRole role);
    void deleteAccount(Long targetId, Long currentUserId);

    /** Sets the target's permission bitmask and revokes their sessions so the new bits apply at next login. */
    AdminAccountDto updatePermissions(Long targetId, Long currentUserId, int permissions);
}
