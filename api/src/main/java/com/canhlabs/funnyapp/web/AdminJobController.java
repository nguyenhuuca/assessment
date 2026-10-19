package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.HasPermission;
import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.admin.ScheduledJobDto;
import com.canhlabs.funnyapp.dto.admin.UpdateScheduledJobRequest;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.service.ScheduledJobService;
import com.canhlabs.funnyapp.utils.AppConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** ADMIN can view the scheduled jobs; only SUPER_ADMIN can change a schedule or run a job now. */
@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/admin/jobs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminJobController {

    private ScheduledJobService scheduledJobService;

    @Autowired
    public void injectScheduledJobService(ScheduledJobService scheduledJobService) {
        this.scheduledJobService = scheduledJobService;
    }

    @GetMapping
    @HasPermission(perm = Permission.ADMIN)
    public ResponseEntity<ResultListInfo<ScheduledJobDto>> list() {
        return ResponseEntity.ok(ResultListInfo.<ScheduledJobDto>builder()
                .status(ResultStatus.SUCCESS).data(scheduledJobService.list()).build());
    }

    /** Next runs for the cron editor; read-only, so ADMIN may call it. Called debounced while typing. */
    @GetMapping("/preview")
    @HasPermission(perm = Permission.ADMIN)
    @RateLimited(permit = 60)
    public ResponseEntity<ResultListInfo<Instant>> preview(@RequestParam String cron,
                                                           @RequestParam(required = false) String zone,
                                                           @RequestParam(defaultValue = "5") int count) {
        return ResponseEntity.ok(ResultListInfo.<Instant>builder()
                .status(ResultStatus.SUCCESS).data(scheduledJobService.preview(cron, zone, count)).build());
    }

    @PatchMapping("/{name}")
    // one @PreAuthorize only: @HasPermission is itself a @PreAuthorize, so its check is inlined here
    @PreAuthorize("hasRole('SUPER_ADMIN') and @permissionServiceImpl.hasPermission(authentication.details.permissions, ADMIN)")
    @AuditLog("updateScheduledJob")
    @RateLimited(permit = 10)
    public ResponseEntity<ResultObjectInfo<ScheduledJobDto>> update(@PathVariable String name,
                                                                    @RequestBody UpdateScheduledJobRequest request) {
        return ResponseEntity.ok(wrap(scheduledJobService.update(name, request)));
    }

    @PostMapping("/{name}/runs")
    @PreAuthorize("hasRole('SUPER_ADMIN') and @permissionServiceImpl.hasPermission(authentication.details.permissions, ADMIN)")
    @AuditLog("runScheduledJob")
    @RateLimited(permit = 5)
    public ResponseEntity<ResultObjectInfo<ScheduledJobDto>> run(@PathVariable String name) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(wrap(scheduledJobService.runNow(name)));
    }

    private static <T> ResultObjectInfo<T> wrap(T data) {
        return ResultObjectInfo.<T>builder().status(ResultStatus.SUCCESS).data(data).build();
    }
}
