package com.canhlabs.assessment.service;

import com.canhlabs.assessment.share.dto.LoginDto;
import com.canhlabs.assessment.share.dto.UserInfoDto;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService  extends UserDetailsService {

    /**
     * Using to join funny system, in case have not exist use, will create new user
     * @param loginDto hold email and password user;
     * @return jwt and user info in case success
     */
    UserInfoDto joinSystem(LoginDto loginDto);

}
