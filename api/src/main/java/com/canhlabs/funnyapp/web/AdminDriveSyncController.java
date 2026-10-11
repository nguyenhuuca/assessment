package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.HasPermission;
import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.admin.DriveSyncListingDto;
import com.canhlabs.funnyapp.dto.admin.DriveSyncRunDto;
import com.canhlabs.funnyapp.dto.admin.StartDriveSyncRequest;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.service.DriveSyncService;
import com.canhlabs.funnyapp.utils.AppConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN can view the Drive sync state; only SUPER_ADMIN can start a force sync. */
@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/admin/drive-sync")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDriveSyncController {

    private DriveSyncService driveSyncService;

    @Autowired
    public void injectDriveSyncService(DriveSyncService driveSyncService) {
        this.driveSyncService = driveSyncService;
    }

    @GetMapping("/files")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<DriveSyncListingDto>> files() {
        return ResponseEntity.ok(wrap(driveSyncService.listFolder()));
    }

    @PostMapping("/runs")
    // one @PreAuthorize only: @HasPermission is itself a @PreAuthorize, so its check is inlined here
    @PreAuthorize("hasRole('SUPER_ADMIN') and @permissionServiceImpl.hasPermission(authentication.details.permissions, ADMIN)")
    @AuditLog("startDriveForceSync")
    @RateLimited(permit = 5)
    public ResponseEntity<ResultObjectInfo<DriveSyncRunDto>> startRun(@RequestBody StartDriveSyncRequest request) {
        DriveSyncRunDto run = driveSyncService.startRun(request == null ? null : request.getFileIds());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(wrap(run));
    }

    @GetMapping("/runs/current")
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultObjectInfo<DriveSyncRunDto>> currentRun() {
        return ResponseEntity.ok(wrap(driveSyncService.currentRun()));
    }

    private static <T> ResultObjectInfo<T> wrap(T data) {
        return ResultObjectInfo.<T>builder().status(ResultStatus.SUCCESS).data(data).build();
    }
}
