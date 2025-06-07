package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.share.dto.LoginDto;
import com.canhlabs.funnyapp.share.dto.MfaRequest;
import com.canhlabs.funnyapp.share.dto.SetupResponse;
import com.canhlabs.funnyapp.share.dto.UserInfoDto;
import com.canhlabs.funnyapp.share.exception.CustomException;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService extends UserDetailsService {

    /**
     * Using to join funny system, in case have not exist use, will create new user
     *
     * @param loginDto hold email and password user;
     * @return jwt and user info in case success
     */
    UserInfoDto joinSystem(LoginDto loginDto);

    String generateSecret();

    String enableMfa(String userName, String secret, String otp);

    /**
     * Using to get QR code to return end user
     * @param userName
     * @return
     */
    SetupResponse setupMfa(String userName);

    UserInfoDto verifyMfa(MfaRequest mfaRequest) ;

    UserInfoDto joinSystemPaswordless(String token);

    String disableMfa(String userName, String otp);


}
