package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.RateLimitAspect;
import com.canhlabs.funnyapp.aop.SlidingWindowRateLimiter;
import com.canhlabs.funnyapp.dto.auth.JwtVerificationResultDto;
import com.canhlabs.funnyapp.dto.payment.OrderDto;
import com.canhlabs.funnyapp.dto.payment.OrderStatusDto;
import com.canhlabs.funnyapp.dto.payment.PlanDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.UnauthorizedException;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.filter.JwtProvider;
import com.canhlabs.funnyapp.service.PaymentOrderService;
import com.canhlabs.funnyapp.utils.AppConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real JWT filter: proves POST /orders reads a JWT when present yet never rejects a guest. */
@AutoConfigureCache
@WebMvcTest(PaymentController.class)
@Import(PaymentControllerTest.TestSecurity.class)
class PaymentControllerTest {

    @TestConfiguration
    static class TestSecurity {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http, JWTAuthenticationFilter jwtFilter) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }

        @Bean
        SlidingWindowRateLimiter slidingWindowRateLimiter() {
            return new SlidingWindowRateLimiter();
        }

        @Bean
        RateLimitAspect rateLimitAspect(SlidingWindowRateLimiter limiter) {
            return new RateLimitAspect(limiter);
        }
    }

    private static final String BASE = AppConstant.API.BASE_URL + "/payments";
    private static final String TOKEN = "Bearer good-token";

    @MockitoBean PaymentOrderService paymentOrderService;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean com.canhlabs.funnyapp.cache.CredentialsVersionCache credentialsVersionCache;

    @Autowired MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtVerificationResultDto ok = new JwtVerificationResultDto();
        ok.setData(UserDetailDto.builder().id(7L).email("u@x.com").build());
        when(jwtProvider.verifyToken(any())).thenAnswer(inv -> {
            if (TOKEN.equals(inv.getArgument(0))) {
                return ok;
            }
            throw new UnauthorizedException("TOKEN_INVALID", 601);
        });
    }

    private static OrderDto orderDto() {
        return OrderDto.builder().code("FMABCD1234").type(PaymentType.DONATION).status(PaymentOrderStatus.PENDING)
                .amount(50000).expiresAt(Instant.now()).qrUrl("https://qr/img?x").memo("FMABCD1234").build();
    }

    @Test
    void plans_isPublic() throws Exception {
        when(paymentOrderService.listActivePlans()).thenReturn(List.of(
                PlanDto.builder().id(1L).code("PREMIUM_1M").name("1m").durationDays(30).priceVnd(99000).build()));

        mockMvc.perform(get(BASE + "/plans").servletPath(BASE + "/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("PREMIUM_1M"))
                .andExpect(jsonPath("$.data[0].priceVnd").value(99000));
    }

    @Test
    void createOrder_guest_passesNullUserId() throws Exception {
        when(paymentOrderService.createOrder(any(), eq(null))).thenReturn(orderDto());

        mockMvc.perform(post(BASE + "/orders").servletPath(BASE + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DONATION\",\"amount\":50000,\"donorName\":\"A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("FMABCD1234"))
                .andExpect(jsonPath("$.data.qrUrl").value("https://qr/img?x"));
    }

    @Test
    void createOrder_withToken_passesUserId() throws Exception {
        when(paymentOrderService.createOrder(any(), eq(7L))).thenReturn(orderDto());

        mockMvc.perform(post(BASE + "/orders").servletPath(BASE + "/orders")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PREMIUM\",\"planId\":1}"))
                .andExpect(status().isOk());
        verify(paymentOrderService).createOrder(any(), eq(7L));
    }

    @Test
    void createOrder_withBadToken_isTreatedAsGuest() throws Exception {
        when(paymentOrderService.createOrder(any(), eq(null))).thenReturn(orderDto());

        mockMvc.perform(post(BASE + "/orders").servletPath(BASE + "/orders")
                        .header("Authorization", "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DONATION\",\"amount\":50000}"))
                .andExpect(status().isOk());
    }

    @Test
    void createOrder_serviceRejects_returnsServiceStatus() throws Exception {
        when(paymentOrderService.createOrder(any(), eq(null)))
                .thenThrow(CustomException.of(HttpStatus.UNAUTHORIZED, 4011, "error.auth.required"));

        mockMvc.perform(post(BASE + "/orders").servletPath(BASE + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PREMIUM\",\"planId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void order_returnsStatusOnly() throws Exception {
        when(paymentOrderService.getByCode("FMABCD1234")).thenReturn(OrderStatusDto.builder()
                .code("FMABCD1234").type(PaymentType.DONATION).status(PaymentOrderStatus.PAID)
                .amount(50000L).expiresAt(Instant.now()).build());

        mockMvc.perform(get(BASE + "/orders/FMABCD1234").servletPath(BASE + "/orders/FMABCD1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.amount").value(50000))
                .andExpect(jsonPath("$.data.donorName").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist());
    }

    @Test
    void order_unknown_is404() throws Exception {
        when(paymentOrderService.getByCode("NOPE"))
                .thenThrow(CustomException.of(HttpStatus.NOT_FOUND, 4041, "error.payment.orderNotFound"));

        mockMvc.perform(get(BASE + "/orders/NOPE").servletPath(BASE + "/orders/NOPE"))
                .andExpect(status().isNotFound());
    }
}
