package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.dto.payment.AdminOrderDto;
import com.canhlabs.funnyapp.dto.payment.AdminPaymentSummaryDto;
import com.canhlabs.funnyapp.dto.payment.AdminPlanDto;
import com.canhlabs.funnyapp.dto.payment.AdminTransactionDto;
import com.canhlabs.funnyapp.dto.payment.PlanRequest;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.enums.PaymentOrderStatus;
import com.canhlabs.funnyapp.enums.PaymentType;
import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.service.AdminPaymentService;
import com.canhlabs.funnyapp.service.FeatureFlagService;
import com.canhlabs.funnyapp.utils.AppConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin payments endpoints: ADMIN gets through, USER is refused (real permission service). */
@AutoConfigureCache
@WebMvcTest(AdminPaymentController.class)
@Import(AdminControllerPermissionTest.TestSecurity.class)
class AdminPaymentControllerTest {

    private static final String BASE = AppConstant.API.BASE_URL + "/admin/payments";
    private static final String JSON = "application/json";

    @MockitoBean FeatureFlagService featureFlagService;
    @MockitoBean JWTAuthenticationFilter jwtFilter;
    @MockitoBean AdminPaymentService service;

    @Autowired MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            inv.<FilterChain>getArgument(2)
               .doFilter(inv.<ServletRequest>getArgument(0), inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
        when(featureFlagService.isEnabled(eq(AppConstant.Flags.PERMISSION_ENFORCEMENT), anyBoolean()))
                .thenReturn(true);
    }

    @Test
    void orders_admin_passesFiltersAndPaging() throws Exception {
        AdminOrderDto dto = AdminOrderDto.builder().id(1L).code("FMABCD1234").type(PaymentType.PREMIUM)
                .status(PaymentOrderStatus.PAID).expectedAmount(99000L).paidAmount(100000).surplus(1000).build();
        when(service.listOrders(eq(PaymentType.PREMIUM), eq(PaymentOrderStatus.PAID), eq(PageRequest.of(1, 5))))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(1, 5), 6));

        mockMvc.perform(get(BASE + "/orders").param("type", "PREMIUM").param("status", "PAID")
                        .param("page", "1").param("size", "5").with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].code").value("FMABCD1234"))
                .andExpect(jsonPath("$.data.content[0].surplus").value(1000));
    }

    @Test
    void orders_sizeIsClampedAndNegativePageFixed() throws Exception {
        when(service.listOrders(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(BASE + "/orders").param("page", "-3").param("size", "5000")
                        .with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk());
        verify(service).listOrders(null, null, PageRequest.of(0, AdminPaymentController.MAX_PAGE_SIZE));
    }

    @Test
    void orders_user_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/orders").with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void unmatched_admin_returnsPage() throws Exception {
        when(service.listUnmatched(PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(
                AdminTransactionDto.builder().sepayId(9L).amount(5000).content("hi").build())));

        mockMvc.perform(get(BASE + "/transactions/unmatched").with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sepayId").value(9));
    }

    @Test
    void unmatched_user_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/transactions/unmatched").with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void summary_admin_returnsTotals() throws Exception {
        when(service.summary()).thenReturn(AdminPaymentSummaryDto.builder().donationTotal(10).donation30d(5)
                .premiumTotal(20).premium30d(7).pendingOrders(2).underpaidOrders(1).unmatchedTransactions(3).build());

        mockMvc.perform(get(BASE + "/summary").with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.donationTotal").value(10))
                .andExpect(jsonPath("$.data.unmatchedTransactions").value(3));
    }

    @Test
    void summary_user_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/summary").with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void link_admin_callsService() throws Exception {
        when(service.linkTransaction(9L, "FMABCD1234"))
                .thenReturn(AdminOrderDto.builder().code("FMABCD1234").status(PaymentOrderStatus.PAID).build());

        mockMvc.perform(post(BASE + "/transactions/9/link").contentType(JSON).content("{\"orderCode\":\"FMABCD1234\"}")
                        .with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));
        verify(service).linkTransaction(9L, "FMABCD1234");
    }

    @Test
    void link_user_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/transactions/9/link").contentType(JSON).content("{\"orderCode\":\"X\"}")
                        .with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void ignore_admin_callsService() throws Exception {
        mockMvc.perform(post(BASE + "/transactions/9/ignore").with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        verify(service).ignoreTransaction(9L);
    }

    @Test
    void ignore_user_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/transactions/9/ignore").with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void resolve_admin_callsService() throws Exception {
        when(service.resolveOrder(4L, true))
                .thenReturn(AdminOrderDto.builder().id(4L).status(PaymentOrderStatus.RESOLVED).build());

        mockMvc.perform(post(BASE + "/orders/4/resolve").contentType(JSON).content("{\"grantPremium\":true}")
                        .with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
        verify(service).resolveOrder(4L, true);
    }

    @Test
    void resolve_user_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/orders/4/resolve").contentType(JSON).content("{\"grantPremium\":false}")
                        .with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void markPaid_admin_callsService() throws Exception {
        when(service.markPaid(4L, 50000L))
                .thenReturn(AdminOrderDto.builder().id(4L).status(PaymentOrderStatus.PAID).build());

        mockMvc.perform(post(BASE + "/orders/4/mark-paid").contentType(JSON).content("{\"amount\":50000}")
                        .with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));
        verify(service).markPaid(4L, 50000L);
    }

    @Test
    void cancel_admin_callsService() throws Exception {
        when(service.cancelOrder(4L))
                .thenReturn(AdminOrderDto.builder().id(4L).status(PaymentOrderStatus.CANCELLED).build());

        mockMvc.perform(post(BASE + "/orders/4/cancel").with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        verify(service).cancelOrder(4L);
    }

    @Test
    void markPaidAndCancel_user_returns403() throws Exception {
        mockMvc.perform(post(BASE + "/orders/4/mark-paid").contentType(JSON).content("{\"amount\":1}")
                        .with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/orders/4/cancel").with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void plans_admin_lists() throws Exception {
        when(service.listPlans()).thenReturn(List.of(AdminPlanDto.builder().id(1L).code("PREMIUM_1M").active(true).build()));

        mockMvc.perform(get(BASE + "/plans").with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("PREMIUM_1M"))
                .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    void plans_user_returns403() throws Exception {
        mockMvc.perform(get(BASE + "/plans").with(authentication(principal(UserRole.USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPlan_admin_callsService() throws Exception {
        when(service.createPlan(any(PlanRequest.class))).thenReturn(AdminPlanDto.builder().id(2L).code("PREMIUM_1Y").build());

        mockMvc.perform(post(BASE + "/plans").contentType(JSON)
                        .content("{\"code\":\"PREMIUM_1Y\",\"name\":\"1 year\",\"durationDays\":365,\"priceVnd\":990000}")
                        .with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("PREMIUM_1Y"));
    }

    @Test
    void updatePlan_admin_callsService() throws Exception {
        when(service.updatePlan(eq(2L), any(PlanRequest.class))).thenReturn(AdminPlanDto.builder().id(2L).build());

        mockMvc.perform(put(BASE + "/plans/2").contentType(JSON)
                        .content("{\"code\":\"PREMIUM_1Y\",\"name\":\"1 year\",\"durationDays\":365,\"priceVnd\":990000}")
                        .with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk());
        verify(service).updatePlan(eq(2L), any(PlanRequest.class));
    }

    @Test
    void setActive_admin_callsService() throws Exception {
        when(service.setPlanActive(2L, true)).thenReturn(AdminPlanDto.builder().id(2L).active(true).build());

        mockMvc.perform(patch(BASE + "/plans/2/active").contentType(JSON).content("{\"active\":true}")
                        .with(authentication(principal(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void planMutations_user_return403() throws Exception {
        mockMvc.perform(post(BASE + "/plans").contentType(JSON).content("{}")
                        .with(authentication(principal(UserRole.USER)))).andExpect(status().isForbidden());
        mockMvc.perform(put(BASE + "/plans/2").contentType(JSON).content("{}")
                        .with(authentication(principal(UserRole.USER)))).andExpect(status().isForbidden());
        mockMvc.perform(patch(BASE + "/plans/2/active").contentType(JSON).content("{\"active\":true}")
                        .with(authentication(principal(UserRole.USER)))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private Authentication principal(UserRole role) {
        UserDetailDto user = UserDetailDto.builder()
                .id(1L).email("tester@test.com")
                .role(role.name()).permissions(Permission.ADMIN.getBit())
                .build();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
        authentication.setDetails(user);
        return authentication;
    }
}
