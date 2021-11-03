package com.canhlabs.assessment.service.impl;

import com.canhlabs.assessment.domain.User;
import com.canhlabs.assessment.repo.UserRepo;
import com.canhlabs.assessment.service.UserService;
import com.canhlabs.assessment.share.dto.LoginDto;
import com.canhlabs.assessment.share.dto.UserInfoDto;
import com.canhlabs.assessment.share.exception.CustomException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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

import static com.canhlabs.assessment.service.impl.Converter.toUserInfo;

@Service
public class UserServiceImpl implements UserService {
    private UserRepo userRepo;
    private PasswordEncoder bCrypt;
    private AuthenticationManager authenticationManager;
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
            return toUserInfo(user);
        }
        // create new user
        User  newUser = toEntity(loginDto);
        newUser = userRepo.save(newUser);
        return toUserInfo(newUser);

    }

    private void validate(LoginDto loginDto) {
        if(StringUtils.isEmpty(loginDto.getEmail()) || StringUtils.isEmpty(loginDto.getPassword())) {
            throw  CustomException.builder()
                    .message("Field is not empty")
                    .build();
        }
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

}
