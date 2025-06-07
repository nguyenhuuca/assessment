package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.service.UserService;
import com.canhlabs.funnyapp.service.impl.InviteService;
import com.canhlabs.funnyapp.share.AppConstant;
import com.canhlabs.funnyapp.share.AppProperties;
import com.canhlabs.funnyapp.share.ResultObjectInfo;
import com.canhlabs.funnyapp.share.dto.DisableRequest;
import com.canhlabs.funnyapp.share.dto.EnableRequest;
import com.canhlabs.funnyapp.share.dto.LoginDto;
import com.canhlabs.funnyapp.share.dto.MfaRequest;
import com.canhlabs.funnyapp.share.dto.SetupResponse;
import com.canhlabs.funnyapp.share.dto.UserInfoDto;
import com.canhlabs.funnyapp.share.enums.ResultStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AppConstant.API.BASE_URL +"/user")
@Validated
@Slf4j

public class UserController extends BaseController {

    private UserService userService;
    private AppProperties appProperties;
    private InviteService inviteService;

    @Autowired
    public void injectInvite(InviteService inviteService) {
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
    @PostMapping("/join")
    public ResponseEntity<ResultObjectInfo<UserInfoDto>> signIn(@RequestBody LoginDto loginDto) {
        UserInfoDto userInfoDto = UserInfoDto.builder().build();
        if(!appProperties.isUsePasswordless()) {
            userInfoDto = userService.joinSystem(loginDto);
        } else {
            inviteService.inviteUser(loginDto.getEmail(), null);
            userInfoDto.setAction("INVITED_SEND");
        }
        return new ResponseEntity<>(ResultObjectInfo.<UserInfoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(userInfoDto)
                .build(), HttpStatus.OK);
    }

    // Using to get QR code incase the end user enabled MFA button
    @GetMapping("/mfa/setup")
    public ResponseEntity<ResultObjectInfo<SetupResponse>> setup(@RequestParam String username) {

        return new ResponseEntity<>(ResultObjectInfo.<SetupResponse>builder()
                .status(ResultStatus.SUCCESS)
                .data(userService.setupMfa(username))
                .build(), HttpStatus.OK);
    }

    // Using to verify the otp in first time end user enable mfa and scan QR
    @PostMapping("/mfa/enable")
    public ResponseEntity<ResultObjectInfo<String>> enable(@RequestBody EnableRequest req) {

        return new ResponseEntity<>(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .data(userService.enableMfa(req.username(), req.secret(), req.otp()))
                .build(), HttpStatus.OK);
    }

    // Using to verify user in case user login and enabled MFA
    @PostMapping("/mfa/verify")
    public ResponseEntity<ResultObjectInfo<UserInfoDto>> verify(@RequestBody MfaRequest req) {
        UserInfoDto rs = userService.verifyMfa(req);
        return new ResponseEntity<>(ResultObjectInfo.<UserInfoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }

    // Using to verify user in case user login and enabled MFA
    @PostMapping("/mfa/disable")
    public ResponseEntity<ResultObjectInfo<String>> verify(@RequestBody DisableRequest req) {
        String rs = userService.disableMfa(req.username(), req.otp());
        return new ResponseEntity<>(ResultObjectInfo.<String>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }

    @GetMapping("/verify-magic")
    public ResponseEntity<ResultObjectInfo<UserInfoDto>> verifyLink(@RequestParam String token) {
        return new ResponseEntity<>(ResultObjectInfo.<UserInfoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(userService.joinSystemPaswordless(token))
                .build(), HttpStatus.OK);
    }

}
