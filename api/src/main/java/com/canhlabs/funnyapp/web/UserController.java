package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.auth.AuthOptionsDto;
import com.canhlabs.funnyapp.dto.auth.PasswordLoginRequest;
import com.canhlabs.funnyapp.dto.auth.RemovePasswordRequest;
import com.canhlabs.funnyapp.dto.auth.SetPasswordRequest;
import com.canhlabs.funnyapp.service.PasswordLoginService;
import com.canhlabs.funnyapp.service.UserService;
import com.canhlabs.funnyapp.service.impl.InviteServiceImpl;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.dto.auth.DisableRequest;
import com.canhlabs.funnyapp.dto.auth.EnableRequest;
import com.canhlabs.funnyapp.dto.auth.LoginDto;
import com.canhlabs.funnyapp.dto.auth.MfaRequest;
import com.canhlabs.funnyapp.dto.auth.SetupResponse;
import com.canhlabs.funnyapp.dto.user.UserInfoDto;
import com.canhlabs.funnyapp.enums.ResultStatus;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AppConstant.API.BASE_URL +"/user")
@Validated
@Slf4j
@AuditLog("Audit all methods in UserController class")
public class UserController extends BaseController {

    private UserService userService;
    private AppProperties appProperties;
    private InviteServiceImpl inviteService;
    private PasswordLoginService passwordLoginService;

    @Autowired
    public void injectPasswordLogin(PasswordLoginService passwordLoginService) {
        this.passwordLoginService = passwordLoginService;
    }

    @Autowired
    public void injectInvite(InviteServiceImpl inviteService) {
        this.inviteService = inviteService;
    }

    @Autowired
    public void injectProp(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Autowired
    public void injectUser(UserService userService) {
        this.userService = userService;
    }

    /**
     * @param loginDto hold the Email and password that client post to server
     * @return toke
     */
    @Operation(summary = "Request a magic link", description = "Sends a sign-in link to the email. Unknown emails are registered when the link is verified, never here.")
    @PostMapping("/join")
    @WithSpan
    @RateLimited(permit = 5)
    public ResponseEntity<ResultObjectInfo<UserInfoDto>> signIn(@RequestBody LoginDto loginDto) {
        // Magic link only: the legacy email+password auto-register path was removed (ADR-0018, pre-hijacking)
        UserInfoDto userInfoDto = UserInfoDto.builder().build();
        inviteService.inviteUser(loginDto.getEmail(), null);
        userInfoDto.setAction("INVITED_SEND");
        return new ResponseEntity<>(ResultObjectInfo.<UserInfoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(userInfoDto)
                .build(), HttpStatus.OK);
    }

    @Operation(summary = "Password login", description = "Email + password sign-in. Every failure returns the same 401 INVALID_CREDENTIALS.")
    @PostMapping("/login")
    @WithSpan
    @RateLimited(permit = 10)
    @AuditLog("Password login")
    public ResponseEntity<ResultObjectInfo<UserInfoDto>> login(@RequestBody PasswordLoginRequest request) {
        return ok(passwordLoginService.login(request));
    }

    @Operation(summary = "Public auth capabilities", description = "Tells the login form whether the password tab should be shown.")
    @GetMapping("/auth-options")
    @WithSpan
    public ResponseEntity<ResultObjectInfo<AuthOptionsDto>> authOptions() {
        return ok(AuthOptionsDto.builder()
                .passwordLoginAvailable(appProperties.isPasswordLoginEnabled())
                .build());
    }

    @Operation(summary = "Set or change password", description = "Authenticated. Revokes every other session and returns a fresh JWT.")
    @PutMapping("/password")
    @WithSpan
    @RateLimited(permit = 5)
    @AuditLog("Set/change password")
    public ResponseEntity<ResultObjectInfo<UserInfoDto>> setPassword(@RequestBody SetPasswordRequest request) {
        return ok(passwordLoginService.setPassword(request));
    }

    @Operation(summary = "Remove password", description = "Authenticated. Back to magic link only; revokes every other session and returns a fresh JWT.")
    @DeleteMapping("/password")
    @WithSpan
    @RateLimited(permit = 5)
    @AuditLog("Remove password")
    public ResponseEntity<ResultObjectInfo<UserInfoDto>> removePassword(@RequestBody RemovePasswordRequest request) {
        return ok(passwordLoginService.removePassword(request));
    }

    private static <T> ResponseEntity<ResultObjectInfo<T>> ok(T data) {
        return new ResponseEntity<>(ResultObjectInfo.<T>builder()
                .status(ResultStatus.SUCCESS)
                .data(data)
                .build(), HttpStatus.OK);
    }

    // Using to get QR code incase the end user enabled MFA button
    @Operation(summary = "Setup MFA for user", description = "This endpoint is used to setup MFA for the user. It returns a QR code that can be scanned by an authenticator app.")
    @GetMapping("/mfa/setup")
    @WithSpan
    public ResponseEntity<ResultObjectInfo<SetupResponse>> setup(@RequestParam String username) {

        return new ResponseEntity<>(ResultObjectInfo.<SetupResponse>builder()
                .status(ResultStatus.SUCCESS)
                .data(userService.setupMfa(username))
                .build(), HttpStatus.OK);
    }

    // Using to verify the otp in first time end user enable mfa and scan QR
    @Operation(summary = "Enable MFA for user", description = "This endpoint is used to enable MFA for the user. It requires the username, secret, and OTP.")
    @PostMapping("/mfa/enable")
    @WithSpan
    public ResponseEntity<ResultObjectInfo<String>> enable(@RequestBody EnableRequest req) {

        return new ResponseEntity<>(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .data(userService.enableMfa(req.username(), req.secret(), req.otp()))
                .build(), HttpStatus.OK);
    }

    @Operation(summary = "Verify MFA for user", description = "This endpoint is used to verify the user with MFA enabled. It requires the username and OTP.")
    // Using to verify user in case user login and enabled MFA
    @PostMapping("/mfa/verify")
    @WithSpan
    public ResponseEntity<ResultObjectInfo<UserInfoDto>> verify(@RequestBody MfaRequest req) {
        UserInfoDto rs = userService.verifyMfa(req);
        return new ResponseEntity<>(ResultObjectInfo.<UserInfoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }

    @Operation(summary = "Disable MFA for user", description = "This endpoint is used to disable MFA for the user. It requires the username and OTP.")
    @PostMapping("/mfa/disable")
    @WithSpan
    public ResponseEntity<ResultObjectInfo<String>> disable(@RequestBody DisableRequest req) {
        String rs = userService.disableMfa(req.username(), req.otp());
        return new ResponseEntity<>(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }

    @Operation(summary = "Verify magic link", description = "This endpoint is used to verify the magic link sent to the user. It requires a token.")
    @GetMapping("/verify-magic")
    @WithSpan
    public ResponseEntity<ResultObjectInfo<UserInfoDto>> verifyLink(@RequestParam String token) {
        return new ResponseEntity<>(ResultObjectInfo.<UserInfoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(userService.joinSystemPaswordless(token))
                .build(), HttpStatus.OK);
    }


    // Using to return current user detail by token
    @Operation(summary = "Get current user details", description = "This endpoint is used to get the details of the currently logged-in user.")
    @GetMapping("/me")
    @WithSpan
    public ResponseEntity<ResultObjectInfo<UserDetailDto>> getCurrent() {
        return new ResponseEntity<>(ResultObjectInfo.<UserDetailDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(userService.getCurrent())
                .build(), HttpStatus.OK);
    }

}
