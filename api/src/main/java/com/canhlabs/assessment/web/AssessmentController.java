package com.canhlabs.assessment.web;

import com.canhlabs.assessment.service.UserService;
import com.canhlabs.assessment.share.AppConstant;
import com.canhlabs.assessment.share.ResultObjectInfo;
import com.canhlabs.assessment.share.dto.LoginDto;
import com.canhlabs.assessment.share.dto.UserInfoDto;
import com.canhlabs.assessment.share.enums.ResultStatus;
import io.swagger.annotations.Api;
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
@Api(tags = {AppConstant.API.TAG_API})
@Validated
@Slf4j
public class AssessmentController extends  BaseController{

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
                .data((UserInfoDto) rs)
                .build(), HttpStatus.OK);
    }

}
