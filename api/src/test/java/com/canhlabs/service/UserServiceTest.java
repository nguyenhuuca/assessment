package com.canhlabs.service;

import com.canhlabs.funnyapp.domain.User;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.service.impl.UserServiceImpl;
import com.canhlabs.funnyapp.share.JwtProvider;
import com.canhlabs.funnyapp.share.dto.LoginDto;
import com.canhlabs.funnyapp.share.dto.TokenDto;
import com.canhlabs.funnyapp.share.dto.UserInfoDto;
import com.canhlabs.funnyapp.share.exception.CustomException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.mockito.Mockito.when;

@Disabled
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Spy
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepo userRepo;
    @Mock
    private JwtProvider jwtProvider;


    BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
    UserServiceImpl userService = new UserServiceImpl();

    @BeforeEach
    void setup() {
        userService.injectBCrypt(bCryptPasswordEncoder);
        userService.injectAuth(authenticationManager);
        userService.injectData(userRepo);
        userService.injectJwt(jwtProvider);
    }

    @Test
    void joinSystemInCaseRegisterTest() {
        LoginDto loginDto = LoginDto.builder()
                .email("ca@gmail.com")
                .password("123456")
                .build();
        User newUser = mockUser();

        when(userRepo.findAllByUserName(loginDto.getEmail())).thenReturn(null);
        when(userRepo.save(ArgumentMatchers.any())).thenReturn(newUser);
        when(jwtProvider.generateToken(ArgumentMatchers.any())).thenReturn(TokenDto.builder().token("test").build());
        UserInfoDto userInfoDto = userService.joinSystem(loginDto);
        Assertions.assertThat(userInfoDto.getJwt()).isEqualTo("test");
    }

    @Test
    void joinSystemInCaseLoginTest() {
        LoginDto loginDto = LoginDto.builder()
                .email("ca@gmail.com")
                .password("123456")
                .build();
        User user = mockUser();

        when(userRepo.findAllByUserName(loginDto.getEmail())).thenReturn(user);
        when(jwtProvider.generateToken(ArgumentMatchers.any())).thenReturn(TokenDto.builder().token("test").build());
        UserInfoDto userInfoDto = userService.joinSystem(loginDto);
        Assertions.assertThat(userInfoDto.getJwt()).isEqualTo("test");
    }

    @Test
    void joinSystemInCaseInvalidEmail() {
        LoginDto loginDto = LoginDto.builder()
                .email("ca")
                .password("123456")
                .build();
        Assertions.assertThatThrownBy(() -> userService.joinSystem(loginDto))
                .isExactlyInstanceOf(CustomException.class)
                .hasMessage("Invalid email");
    }

    private User mockUser() {
        return User.builder()
                .id(1L)
                .password(bCryptPasswordEncoder.encode("123456"))
                .userName("ca@gmail.com")
                .build();

    }

}
