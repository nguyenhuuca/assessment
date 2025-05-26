package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.domain.User;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.service.UserService;
import com.canhlabs.funnyapp.share.AppUtils;
import com.canhlabs.funnyapp.share.JwtProvider;
import com.canhlabs.funnyapp.share.dto.JwtGenerationDto;
import com.canhlabs.funnyapp.share.dto.LoginDto;
import com.canhlabs.funnyapp.share.dto.UserDetailDto;
import com.canhlabs.funnyapp.share.dto.UserInfoDto;
import com.canhlabs.funnyapp.share.exception.CustomException;
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

import java.util.HashSet;
import java.util.Set;

import static com.canhlabs.funnyapp.service.impl.Converter.toUserInfo;

@Service
public class UserServiceImpl implements UserService {
    private UserRepo userRepo;
    private PasswordEncoder bCrypt;
    private JwtProvider jwtProvider;
    private AuthenticationManager authenticationManager;

    @Autowired
    public void injectJwt(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }
    @Lazy
    @Autowired
    public void injectAuth(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Autowired
    public  void injectData(UserRepo userRepo) {
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
        if(user != null) {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginDto.getEmail(),
                    loginDto.getPassword());
            authenticationManager.authenticate(authenticationToken);
            return toUserInfo(user, getToken(user));
        }
        // create new user
        User  newUser = toEntity(loginDto);
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
        if(StringUtils.isEmpty(loginDto.getEmail()) || StringUtils.isEmpty(loginDto.getPassword())) {
            throw  CustomException.builder()
                    .message("Field is not empty")
                    .build();
        }

        if(!AppUtils.isValidEmail(loginDto.getEmail())) {
            throw  CustomException.builder()
                    .message("Invalid email")
                    .build();
        }
    }

}
