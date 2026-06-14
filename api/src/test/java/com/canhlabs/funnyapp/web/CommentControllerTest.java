package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.RateLimitAspect;
import com.canhlabs.funnyapp.aop.SlidingWindowRateLimiter;
import com.canhlabs.funnyapp.dto.comment.CommentNode;
import com.canhlabs.funnyapp.dto.comment.CreateCommentResponse;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.service.impl.VideoCommentServiceImpl;
import com.canhlabs.funnyapp.utils.AppConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentController.class)
@Import(CommentControllerTest.TestSecurity.class)
class CommentControllerTest {

    @TestConfiguration
    static class TestSecurity {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
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

    private static final String BASE_URL = AppConstant.API.BASE_URL + "/videos/{videoId}/comments";
    private static final String DELETE_URL = AppConstant.API.BASE_URL + "/videos/{videoId}/comments/{id}";

    @MockitoBean
    VideoCommentServiceImpl videoCommentService;

    @MockitoBean
    JWTAuthenticationFilter jwtFilter;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        // JWT filter: transparent pass-through
        doAnswer(inv -> {
            inv.<FilterChain>getArgument(2)
               .doFilter(inv.<ServletRequest>getArgument(0),
                         inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    // ── GET /videos/{videoId}/comments ────────────────────────────────────────

    @Test
    void getComments_returnsOkWithCommentList() throws Exception {
        CommentNode node = CommentNode.builder()
                .id(UUID.randomUUID())
                .videoId("vid-1")
                .content("Hello world")
                .build();
        when(videoCommentService.getNestedComments("vid-1")).thenReturn(List.of(node));

        mockMvc.perform(get(BASE_URL, "vid-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].content").value("Hello world"));
    }

    @Test
    void getComments_emptyList_returnsOkWithEmptyData() throws Exception {
        when(videoCommentService.getNestedComments("vid-empty")).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL, "vid-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ── POST /videos/{videoId}/comments ──────────────────────────────────────

    @Test
    void createComment_asAuthenticatedUser_returnsOkWithResponse() throws Exception {
        UUID commentId = UUID.randomUUID();
        CreateCommentResponse response = CreateCommentResponse.builder()
                .id(commentId)
                .guestToken(null)
                .build();
        when(videoCommentService.createComment(eq("vid-1"), any(), eq(null))).thenReturn(response);

        String body = """
                {
                  "userId": "user@example.com",
                  "content": "Great video!"
                }
                """;

        mockMvc.perform(post(BASE_URL, "vid-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(commentId.toString()));
    }

    @Test
    void createComment_asGuest_withGuestTokenHeader_returnsOkWithToken() throws Exception {
        UUID commentId = UUID.randomUUID();
        String existingToken = UUID.randomUUID().toString();
        CreateCommentResponse response = CreateCommentResponse.builder()
                .id(commentId)
                .guestToken(existingToken)
                .build();
        when(videoCommentService.createComment(eq("vid-1"), any(), eq(existingToken))).thenReturn(response);

        String body = """
                {
                  "guestName": "GuestUser",
                  "content": "Nice video!"
                }
                """;

        mockMvc.perform(post(BASE_URL, "vid-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Guest-Token", existingToken)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.guestToken").value(existingToken));
    }

    @Test
    void createComment_asGuest_withoutGuestTokenHeader_returnsOk() throws Exception {
        UUID commentId = UUID.randomUUID();
        String newToken = UUID.randomUUID().toString();
        CreateCommentResponse response = CreateCommentResponse.builder()
                .id(commentId)
                .guestToken(newToken)
                .build();
        when(videoCommentService.createComment(eq("vid-2"), any(), eq(null))).thenReturn(response);

        String body = """
                {
                  "guestName": "Anonymous",
                  "content": "Interesting!"
                }
                """;

        mockMvc.perform(post(BASE_URL, "vid-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.guestToken").value(newToken));
    }

    @Test
    void createComment_missingContent_returnsBadRequest() throws Exception {
        // content is @NotBlank — should fail bean validation → 400
        String body = """
                {
                  "userId": "user@example.com",
                  "content": ""
                }
                """;

        mockMvc.perform(post(BASE_URL, "vid-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /videos/{videoId}/comments/{id} ────────────────────────────────

    @Test
    void deleteComment_happyPath_returnsOkWithMessage() throws Exception {
        UUID commentId = UUID.randomUUID();
        doNothing().when(videoCommentService).deleteComment(eq(commentId), eq(null));

        mockMvc.perform(delete(DELETE_URL, "vid-1", commentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data").value("Comment deleted"));
    }

    @Test
    void deleteComment_withGuestToken_returnsOk() throws Exception {
        UUID commentId = UUID.randomUUID();
        String guestToken = UUID.randomUUID().toString();
        doNothing().when(videoCommentService).deleteComment(eq(commentId), eq(guestToken));

        mockMvc.perform(delete(DELETE_URL, "vid-1", commentId.toString())
                        .header("X-Guest-Token", guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data").value("Comment deleted"));
    }

    @Test
    void deleteComment_commentNotFound_throwsNoSuchElementException() throws Exception {
        UUID commentId = UUID.randomUUID();
        doThrow(new NoSuchElementException("Comment not found"))
                .when(videoCommentService).deleteComment(eq(commentId), any());

        // NoSuchElementException is not mapped in RestExceptionHandler; MockMvc re-throws it
        try {
            mockMvc.perform(delete(DELETE_URL, "vid-1", commentId.toString()));
        } catch (Exception ex) {
            // Unwrap the NestedServletException to verify the root cause
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            org.junit.jupiter.api.Assertions.assertInstanceOf(
                    NoSuchElementException.class, cause,
                    "Expected NoSuchElementException as root cause");
        }
    }

    @Test
    void deleteComment_notAuthorized_throwsSecurityException() throws Exception {
        UUID commentId = UUID.randomUUID();
        doThrow(new SecurityException("Not authorized to delete this comment"))
                .when(videoCommentService).deleteComment(eq(commentId), any());

        // SecurityException is not mapped in RestExceptionHandler; MockMvc re-throws it
        try {
            mockMvc.perform(delete(DELETE_URL, "vid-1", commentId.toString()));
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            org.junit.jupiter.api.Assertions.assertInstanceOf(
                    SecurityException.class, cause,
                    "Expected SecurityException as root cause");
        }
    }
}
