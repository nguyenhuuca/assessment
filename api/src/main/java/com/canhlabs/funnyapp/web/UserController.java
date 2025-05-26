package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.service.UserService;
import com.canhlabs.funnyapp.share.AppConstant;
import com.canhlabs.funnyapp.share.ResultObjectInfo;
import com.canhlabs.funnyapp.share.dto.LoginDto;
import com.canhlabs.funnyapp.share.dto.UserInfoDto;
import com.canhlabs.funnyapp.share.enums.ResultStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AppConstant.API.BASE_URL)
@Validated
@Slf4j
public class UserController extends  BaseController{

    private UserService userService;

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
        UserInfoDto rs = userService.joinSystem(loginDto);
        return new ResponseEntity<>(ResultObjectInfo.<UserInfoDto>builder()
                .status(ResultStatus.SUCCESS)
                .data(rs)
                .build(), HttpStatus.OK);
    }

}
