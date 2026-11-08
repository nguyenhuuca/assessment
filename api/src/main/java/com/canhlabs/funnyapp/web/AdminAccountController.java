package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.HasPermission;
import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.admin.AdminAccountBulkRequest;
import com.canhlabs.funnyapp.dto.admin.AdminAccountDto;
import com.canhlabs.funnyapp.dto.admin.BulkResultDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.service.AdminAccountBulkService;
import com.canhlabs.funnyapp.service.AdminAccountService;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.utils.AppUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    private AdminAccountService adminAccountService;
    private AdminAccountBulkService adminAccountBulkService;

    @Autowired
    public void injectAdminAccountBulkService(AdminAccountBulkService adminAccountBulkService) {
        this.adminAccountBulkService = adminAccountBulkService;
    }

    @Autowired
    public void injectAdminAccountService(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @GetMapping("/accounts")
    public ResponseEntity<ResultObjectInfo<Page<AdminAccountDto>>> getAccounts(
            // Fixed sort: without ORDER BY Postgres returns rows in physical order, so an updated account jumped around
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<AdminAccountDto> data = adminAccountService.getAccounts(pageable);
        return ResponseEntity.ok(ResultObjectInfo.<Page<AdminAccountDto>>builder()
                .status(ResultStatus.SUCCESS)
                .data(data)
                .build());
    }

    @PatchMapping("/accounts/{id}/role")
    public ResponseEntity<ResultObjectInfo<String>> updateAccountRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        UserDetailDto current = AppUtils.getCurrentUser();
        UserRole role = UserRole.valueOf(body.get("role"));
        adminAccountService.updateRole(id, current.getId(), role);
        return ResponseEntity.ok(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .message("Role updated")
                .build());
    }

    @PatchMapping("/accounts/{id}/permissions")
    public ResponseEntity<ResultObjectInfo<AdminAccountDto>> updateAccountPermissions(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        Integer permissions = body.get("permissions");
        if (permissions == null) {
            throw new IllegalArgumentException("permissions is required");
        }
        UserDetailDto current = AppUtils.getCurrentUser();
        return ResponseEntity.ok(ResultObjectInfo.<AdminAccountDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(adminAccountService.updatePermissions(id, current.getId(), permissions))
                .build());
    }

    @PostMapping("/accounts/bulk")
    @AuditLog("bulkAccounts")
    @HasPermission(perm = Permission.ADMIN)
    @RateLimited(permit = 10)
    public ResponseEntity<ResultObjectInfo<BulkResultDto>> bulkAccounts(
            @Valid @RequestBody AdminAccountBulkRequest request) {
        UserDetailDto current = AppUtils.getCurrentUser();
        BulkResultDto data = adminAccountBulkService.bulk(request, current.getId());
        return ResponseEntity.ok(ResultObjectInfo.<BulkResultDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(data)
                .build());
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<ResultObjectInfo<String>> deleteAccount(@PathVariable Long id) {
        UserDetailDto current = AppUtils.getCurrentUser();
        adminAccountService.deleteAccount(id, current.getId());
        return ResponseEntity.ok(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .message("Account deleted")
                .build());
    }
}
