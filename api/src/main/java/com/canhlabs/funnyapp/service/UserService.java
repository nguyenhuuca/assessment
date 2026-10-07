package com.canhlabs.funnyapp.service;

import com.canhlabs.funnyapp.dto.auth.LoginDto;
import com.canhlabs.funnyapp.dto.auth.MfaRequest;
import com.canhlabs.funnyapp.dto.auth.SetupResponse;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.user.UserInfoDto;
import com.canhlabs.funnyapp.entity.User;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService extends UserDetailsService {

    /**
     * Finishes a successful first-factor authentication: MFA users get {@code MFA_REQUIRED} plus a session token
     * (completed via {@code verifyMfa}), everyone else gets a JWT.
     */
    UserInfoDto completeLogin(User user);

    /** @return a signed JWT for the user reflecting its current role, permissions and credentials version */
    String issueToken(User user);

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

    UserDetailDto getCurrent();


}
