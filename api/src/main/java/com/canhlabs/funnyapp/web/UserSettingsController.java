package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.usersettings.DeleteAccountRequest;
import com.canhlabs.funnyapp.dto.usersettings.UpdateUserSettingsRequest;
import com.canhlabs.funnyapp.dto.usersettings.UserSettingsDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.service.UserSettingsService;
import com.canhlabs.funnyapp.utils.AppConstant;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/user")
@AuditLog("Audit all methods in UserSettingsController class")
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    public UserSettingsController(UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
    }

    @Operation(summary = "Get user settings", description = "Returns the authenticated user's settings, creating a defaults row on first call.")
    @GetMapping("/settings")
    public ResponseEntity<ResultObjectInfo<UserSettingsDto>> getSettings() {
        return new ResponseEntity<>(ResultObjectInfo.<UserSettingsDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(userSettingsService.getSettings())
                .build(), HttpStatus.OK);
    }

    @Operation(summary = "Update user settings", description = "Partially updates editable preference fields (atomic — all fields validated before any write).")
    @PatchMapping("/settings")
    @RateLimited(permit = 20)
    public ResponseEntity<ResultObjectInfo<UserSettingsDto>> updateSettings(@RequestBody UpdateUserSettingsRequest request) {
        return new ResponseEntity<>(ResultObjectInfo.<UserSettingsDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(userSettingsService.updateSettings(request))
                .build(), HttpStatus.OK);
    }

    @Operation(summary = "Delete account", description = "Soft-deletes and anonymizes the authenticated user's account after step-up verification.")
    @DeleteMapping("/account")
    @RateLimited(permit = 3)
    public ResponseEntity<Void> deleteAccount(@RequestBody DeleteAccountRequest request) {
        userSettingsService.deleteAccount(request);
        return ResponseEntity.noContent().build();
    }
}
