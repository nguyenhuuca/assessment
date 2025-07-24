package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.dto.DisableRequest;
import com.canhlabs.funnyapp.dto.EnableRequest;
import com.canhlabs.funnyapp.dto.LoginDto;
import com.canhlabs.funnyapp.dto.MfaRequest;
import com.canhlabs.funnyapp.dto.SetupResponse;
import com.canhlabs.funnyapp.dto.UserDetailDto;
import com.canhlabs.funnyapp.dto.UserInfoDto;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
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

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userController.injectUser(userService);
        userController.injectProp(appProperties);
        userController.injectInvite(inviteService);
    }

    @Test
    void signIn_shouldJoinSystem_whenPasswordlessIsFalse() {
        LoginDto loginDto = new LoginDto();
        UserInfoDto userInfo = UserInfoDto.builder().build();
        when(appProperties.isUsePasswordless()).thenReturn(false);
        when(userService.joinSystem(loginDto)).thenReturn(userInfo);

        ResponseEntity<ResultObjectInfo<UserInfoDto>> response = userController.signIn(loginDto);

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals(userInfo, response.getBody().getData());
        verify(userService).joinSystem(loginDto);
        verify(inviteService, never()).inviteUser(anyString(), any());
    }

    @Test
    void signIn_shouldInviteUser_whenPasswordlessIsTrue() {
        LoginDto loginDto = new LoginDto();
        loginDto.setEmail("test@example.com");
        UserInfoDto userInfo = UserInfoDto.builder().build();
        when(appProperties.isUsePasswordless()).thenReturn(true);

        ResponseEntity<ResultObjectInfo<UserInfoDto>> response = userController.signIn(loginDto);

        assertEquals(ResultStatus.SUCCESS, response.getBody().getStatus());
        assertEquals("INVITED_SEND", response.getBody().getData().getAction());
        verify(inviteService).inviteUser("test@example.com", null);
        verify(userService, never()).joinSystem(any());
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
}