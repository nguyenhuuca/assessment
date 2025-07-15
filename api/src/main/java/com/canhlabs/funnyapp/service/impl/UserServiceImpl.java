package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.cache.MFASessionStore;
import com.canhlabs.funnyapp.domain.User;
import com.canhlabs.funnyapp.domain.UserEmailRequest;
import com.canhlabs.funnyapp.dto.JwtGenerationDto;
import com.canhlabs.funnyapp.dto.LoginDto;
import com.canhlabs.funnyapp.dto.MfaRequest;
import com.canhlabs.funnyapp.dto.SetupResponse;
import com.canhlabs.funnyapp.dto.UserDetailDto;
import com.canhlabs.funnyapp.dto.UserInfoDto;
import com.canhlabs.funnyapp.filter.JwtProvider;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.service.UserService;
import com.canhlabs.funnyapp.share.AppUtils;
import com.canhlabs.funnyapp.share.QrUtil;
import com.canhlabs.funnyapp.share.totp.Totp;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.canhlabs.funnyapp.service.impl.Converter.toUserInfo;
import static com.canhlabs.funnyapp.share.exception.CustomException.raiseErr;

@Slf4j
@Service
public class UserServiceImpl implements UserService {
    private UserRepo userRepo;
    private PasswordEncoder bCrypt;
    private JwtProvider jwtProvider;
    private AuthenticationManager authenticationManager;
    private MFASessionStore mfaSessionStore;
    private InviteServiceImpl inviteService;
    private Totp totp;

    @Autowired
    public void injectTotp(Totp totp) {
        this.totp = totp;
    }

    @Autowired
    public void injectInvite(InviteServiceImpl inviteService) {
        this.inviteService = inviteService;
    }

    @Autowired
    public void injectJwt(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Autowired
    public void injectMfaStore(MFASessionStore mfaSessionStore) {
        this.mfaSessionStore = mfaSessionStore;
    }

    @Lazy
    @Autowired
    public void injectAuth(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Autowired
    public void injectData(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @Autowired
    public void injectBCrypt(PasswordEncoder bCrypt) {
        this.bCrypt = bCrypt;
    }

    @Transactional
    @Override
    public UserInfoDto joinSystem(LoginDto loginDto) {
        validate(loginDto);
        User user = userRepo.findAllByUserName(loginDto.getEmail());
        if (user != null) {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginDto.getEmail(),
                    loginDto.getPassword());
            authenticationManager.authenticate(authenticationToken);
            if (user.isMfaEnabled()) {
                String sessionToken = UUID.randomUUID().toString();
                mfaSessionStore.storeSession(sessionToken, user.getUserName());
                return toUserInfo(user, null, "MFA_REQUIRED", sessionToken);

            }
            return toUserInfo(user, getToken(user));
        }
        // create new user
        User newUser = toEntity(loginDto);
        newUser = userRepo.save(newUser);
        return toUserInfo(newUser, getToken(newUser));

    }

    @Override
    public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {
        User user = userRepo.findAllByUserName(userName);
        if (user.getPassword() == null) {
            throw new UsernameNotFoundException(userName);
        }
        Set<GrantedAuthority> grantedAuthorities = new HashSet<>();
        return new org.springframework.security.core.userdetails.User(user.getUserName(), user.getPassword(), grantedAuthorities);
    }

    @Override
    public String generateSecret() {
        byte[] buffer = new byte[10]; // 80 bits
        new SecureRandom().nextBytes(buffer);
        return new Base32().encodeToString(buffer).replace("=", "");
    }

    @Override
    public String enableMfa(String userName, String secret, String otp) {
        if (!totp.verify(otp, secret)) {
            raiseErr("Otp is incorrectly");

        }
        User user = userRepo.findAllByUserName(userName);
        user.setMfaEnabled(true);
        user.setMfaSecret(secret);
        userRepo.save(user);
        return "success";
    }

    @Override
    public SetupResponse setupMfa(String userName) {
        String secret = generateSecret();
        String issuer = "canh-labs";
        String otpAuthUrl = String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=6&period=30",
                issuer, userName, secret, issuer
        );
        String qrCode = QrUtil.generateQRCodeBase64(otpAuthUrl, 200, 200);
        return new SetupResponse(secret, qrCode);
    }

    @Override
    public UserInfoDto verifyMfa(MfaRequest mfaRequest) {
        String sessionToken = mfaRequest.sessionToken();
        Optional<String> userIdOpt = mfaSessionStore.getUserId(sessionToken);
        if (userIdOpt.isEmpty()) {
            raiseErr("Invalid or expired session");
        }
        User user = userRepo.findAllByUserName(userIdOpt.get());
        if (!totp.verify(mfaRequest.otp(), user.getMfaSecret())) {
            raiseErr("Otp is incorrectly");
        }
        return toUserInfo(user, getToken(user));
    }

    @Override
    public UserInfoDto joinSystemPaswordless(String token) {
        Optional<UserEmailRequest> userReq = inviteService.verifyToken(token);
        if (userReq.isEmpty()) {
            raiseErr("Token in invalid");
        }
        log.info("token was verified success for user {}", userReq.get().getEmail());

        User user = userRepo.findAllByUserName(userReq.get().getEmail());
        if (user != null) {
            inviteService.markTokenAsUsed(userReq.get(), userReq.get().getUserId());
            if (user.isMfaEnabled()) {
                String sessionToken = UUID.randomUUID().toString();
                mfaSessionStore.storeSession(sessionToken, user.getUserName());
                return toUserInfo(user, null, "MFA_REQUIRED", sessionToken);
            }
            return toUserInfo(user, getToken(user));
        }
        // create new user
        User newUser = User.builder()
                .userName(userReq.get().getEmail())
                .build();
        newUser = userRepo.save(newUser);
        inviteService.markTokenAsUsed(userReq.get(), userReq.get().getUserId());
        return toUserInfo(newUser, getToken(newUser));
    }

    @Override
    public String disableMfa(String userName, String otp) {
        User user = userRepo.findAllByUserName(userName);
        if (user == null) {
            raiseErr("User not exist");
        }
        if (!user.isMfaEnabled()) {
            raiseErr("Mfa already disabled");
        }
        boolean isValid = totp.verify(otp, user.getMfaSecret());
        if (!isValid) raiseErr("Otp is invalid!");

        user.setMfaEnabled(false);
        userRepo.save(user);
        return "success";
    }

    @Override
    public UserDetailDto getCurrent() {
        return AppUtils.getCurrentUser();
    }

    private User toEntity(LoginDto loginDto) {
        return User.builder()
                .userName(loginDto.getEmail())
                .password(bCrypt.encode(loginDto.getPassword()))
                .build();
    }

    private String getToken(User user) {
        return jwtProvider.generateToken(JwtGenerationDto.builder()
                .payload(UserDetailDto.builder().id(user.getId()).email(user.getUserName()).build())
                .build()).getToken();
    }

    private void validate(LoginDto loginDto) {
        if (StringUtils.isEmpty(loginDto.getEmail()) || StringUtils.isEmpty(loginDto.getPassword())) {
            raiseErr("Field is not empty");
        }

        if (!AppUtils.isValidEmail(loginDto.getEmail())) {
            raiseErr("Invalid email");
        }
    }

}
