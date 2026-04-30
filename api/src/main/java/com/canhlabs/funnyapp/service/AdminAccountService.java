package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.AdminAccountDto;
import com.canhlabs.funnyapp.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminAccountService {
    Page<AdminAccountDto> getAccounts(Pageable pageable);
    void updateRole(Long targetId, Long currentUserId, UserRole role);
    void deleteAccount(Long targetId, Long currentUserId);
}
