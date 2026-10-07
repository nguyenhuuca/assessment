package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.auth.DisableRequest;
import com.canhlabs.funnyapp.dto.auth.EnableRequest;
import com.canhlabs.funnyapp.dto.auth.LoginDto;
import com.canhlabs.funnyapp.dto.auth.MfaRequest;
import com.canhlabs.funnyapp.dto.auth.SetupResponse;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.user.UserInfoDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.auth.AuthOptionsDto;
import com.canhlabs.funnyapp.dto.auth.PasswordLoginRequest;
import com.canhlabs.funnyapp.dto.auth.RemovePasswordRequest;
import com.canhlabs.funnyapp.dto.auth.SetPasswordRequest;
import com.canhlabs.funnyapp.service.PasswordLoginService;
import com.canhlabs.funnyapp.service.UserService;
import com.canhlabs.funnyapp.service.impl.InviteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private AppProperties appProperties;
    @Mock
    private InviteServiceImpl inviteService;
    @Mock
    private PasswordLoginService passwordLoginService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userController.injectUser(userService);
        userController.injectProp(appProperties);
        userController.injectInvite(inviteService);
        userController.injectPasswordLogin(passwordLoginService);
    }

    @Test
    void signIn_alwaysSendsMagicLink_neverAutoRegistersWithPassword() {
        LoginDto loginDto = new LoginDto();
        loginDto.setEmail("test@example.com");
        loginDto.setPassword("whatever-password");

        ResponseEntity<ResultObjectInfo<UserInfoDto>> response = userController.signIn(loginDto);

        assertEquals("INVITED_SEND", response.getBody().getData().getAction());
        verify(inviteService).inviteUser("test@example.com", null);
        // legacy joinSystem removed (ADR-0018): /user/join only sends magic links
    }

    @Test
    void signIn_shouldInviteUser() {
        LoginDto loginDto = new LoginDto();
        loginDto.setEmail("test@example.com");
        UserInfoDto userInfo = UserInfoDto.builder().build();

        ResponseEntity<ResultObjectInfo<UserInfoDto>> response = userController.signIn(loginDto);

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals("INVITED_SEND", response.getBody().getData().getAction());
        verify(inviteService).inviteUser("test@example.com", null);
        // legacy joinSystem removed (ADR-0018): /user/join only sends magic links
    }

    @Test
    void setup_shouldReturnSetupResponse() {
        String username = "user";
        SetupResponse setupResponse = new SetupResponse("secret", "code");
        when(userService.setupMfa(username)).thenReturn(setupResponse);

        ResponseEntity<ResultObjectInfo<SetupResponse>> response = userController.setup(username);

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals(setupResponse, response.getBody().getData());
    }

    @Test
    void enable_shouldReturnSuccess() {
        EnableRequest req = new EnableRequest("user", "secret", "otp");
        when(userService.enableMfa("user", "secret", "otp")).thenReturn("enabled");

        ResponseEntity<ResultObjectInfo<String>> response = userController.enable(req);

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
    }

    @Test
    void verify_shouldReturnUserInfo() {
        MfaRequest req = new MfaRequest("user", "otp", "sessionToken");
        UserInfoDto userInfo = UserInfoDto.builder().build();
        when(userService.verifyMfa(req)).thenReturn(userInfo);
        when(userService.verifyMfa(req)).thenReturn(userInfo);

        ResponseEntity<ResultObjectInfo<UserInfoDto>> response = userController.verify(req);

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals(userInfo, response.getBody().getData());
    }

    @Test
    void disable_shouldReturnSuccess() {
        DisableRequest req = new DisableRequest("user", "otp");
        when(userService.disableMfa("user", "otp")).thenReturn("disabled");

        ResponseEntity<ResultObjectInfo<String>> response = userController.disable(req);

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals("disabled", response.getBody().getData());
    }

    @Test
    void verifyLink_shouldReturnUserInfo() {
        String token = "token";
        UserInfoDto userInfo = UserInfoDto.builder().build();
        when(userService.joinSystemPaswordless(token)).thenReturn(userInfo);

        ResponseEntity<ResultObjectInfo<UserInfoDto>> response = userController.verifyLink(token);

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals(userInfo, response.getBody().getData());
    }


    @Test
    void getCurrent_shouldReturnCurrentUserDetail() {
        UserDetailDto userDetail = UserDetailDto.builder().id(1L).email("test@abc.com").build();
        when(userService.getCurrent()).thenReturn(userDetail);

        ResponseEntity<ResultObjectInfo<UserDetailDto>> response = userController.getCurrent();

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals(userDetail, response.getBody().getData());
        verify(userService).getCurrent();
    }

    @Test
    void login_delegatesToPasswordLoginService() {
        PasswordLoginRequest req = new PasswordLoginRequest("a@b.com", "pw");
        UserInfoDto info = UserInfoDto.builder().jwt("jwt").build();
        when(passwordLoginService.login(req)).thenReturn(info);

        ResponseEntity<ResultObjectInfo<UserInfoDto>> response = userController.login(req);

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals(info, response.getBody().getData());
    }

    @Test
    void authOptions_reflectsKillSwitch() {
        when(appProperties.isPasswordLoginEnabled()).thenReturn(true);
        AuthOptionsDto on = userController.authOptions().getBody().getData();
        when(appProperties.isPasswordLoginEnabled()).thenReturn(false);
        AuthOptionsDto off = userController.authOptions().getBody().getData();

        assertEquals(true, on.isPasswordLoginAvailable());
        assertEquals(false, off.isPasswordLoginAvailable());
    }

    @Test
    void setAndRemovePassword_delegate() {
        SetPasswordRequest set = new SetPasswordRequest(null, "a-long-new-password", null);
        RemovePasswordRequest remove = new RemovePasswordRequest("cur", null);
        UserInfoDto info = UserInfoDto.builder().jwt("jwt").build();
        when(passwordLoginService.setPassword(set)).thenReturn(info);
        when(passwordLoginService.removePassword(remove)).thenReturn(info);

        assertEquals(info, userController.setPassword(set).getBody().getData());
        assertEquals(info, userController.removePassword(remove).getBody().getData());
    }

    @Test
    void passwordEndpoints_haveRateLimitAndAudit() throws NoSuchMethodException {
        var login = UserController.class.getMethod("login", PasswordLoginRequest.class);
        var put = UserController.class.getMethod("setPassword", SetPasswordRequest.class);
        var del = UserController.class.getMethod("removePassword", RemovePasswordRequest.class);

        assertEquals(10, login.getAnnotation(RateLimited.class).permit());
        assertEquals(5, put.getAnnotation(RateLimited.class).permit());
        assertEquals(5, del.getAnnotation(RateLimited.class).permit());
        for (var m : new java.lang.reflect.Method[]{login, put, del}) {
            org.junit.jupiter.api.Assertions.assertNotNull(m.getAnnotation(AuditLog.class));
        }
    }
}
