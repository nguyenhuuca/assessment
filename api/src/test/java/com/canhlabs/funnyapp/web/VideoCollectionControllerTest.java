package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.RateLimitAspect;
import com.canhlabs.funnyapp.aop.SlidingWindowRateLimiter;
import com.canhlabs.funnyapp.dto.auth.JwtVerificationResultDto;
import com.canhlabs.funnyapp.dto.collection.CollectionDto;
import com.canhlabs.funnyapp.dto.collection.CollectionRequest;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.video.VideoDto;
import com.canhlabs.funnyapp.enums.CollectionVisibility;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.UnauthorizedException;
import com.canhlabs.funnyapp.filter.JWTAuthenticationFilter;
import com.canhlabs.funnyapp.filter.JwtProvider;
import com.canhlabs.funnyapp.service.VideoCollectionService;
import com.canhlabs.funnyapp.utils.AppConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real JWTAuthenticationFilter (JwtProvider mocked) so public vs protected routes are proven end to end. */
@WebMvcTest(VideoCollectionController.class)
@Import(VideoCollectionControllerTest.TestSecurity.class)
class VideoCollectionControllerTest {

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

    private static final String BASE = AppConstant.API.BASE_URL + "/collections";
    private static final String TOKEN = "Bearer good-token";
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @MockitoBean
    VideoCollectionService collectionService;

    @MockitoBean
    JwtProvider jwtProvider;

    @MockitoBean
    com.canhlabs.funnyapp.cache.CredentialsVersionCache credentialsVersionCache;

    @Autowired
    MockMvc mockMvc;

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

    private static CollectionDto dto(String name, boolean isDefault) {
        return CollectionDto.builder().id(ID).name(name).visibility(CollectionVisibility.PRIVATE)
                .isDefault(isDefault).itemCount(2).unavailableCount(1L).canManage(true).build();
    }

    private static CustomException error(HttpStatus status) {
        return CustomException.builder().status(status).subCode(status.value()).message("x").build();
    }

    @Test
    void getMine_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(BASE).servletPath(BASE)).andExpect(status().isUnauthorized());
        verify(collectionService, never()).getMine();
    }

    @Test
    void getMine_returnsListWithIsDefaultFlag() throws Exception {
        when(collectionService.getMine()).thenReturn(List.of(dto("Xem sau", true)));

        mockMvc.perform(get(BASE).servletPath(BASE).header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Xem sau"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[0].default").doesNotExist())
                .andExpect(jsonPath("$.data[0].canManage").value(true));
    }

    @Test
    void getPublic_guestAllowed() throws Exception {
        when(collectionService.getPublic()).thenReturn(List.of(dto("Hot", false)));

        mockMvc.perform(get(BASE + "/public").servletPath(BASE + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Hot"));
    }

    @Test
    void getPublic_badTokenTreatedAsGuest() throws Exception {
        when(collectionService.getPublic()).thenReturn(List.of());

        mockMvc.perform(get(BASE + "/public").servletPath(BASE + "/public").header("Authorization", "Bearer junk"))
                .andExpect(status().isOk());
    }

    @Test
    void getVideos_guestAllowed_andPrivateAnswers404() throws Exception {
        String url = BASE + "/" + ID + "/videos";
        VideoDto video = new VideoDto();
        video.setId(5L);
        when(collectionService.getVideos(ID, 0, 24)).thenReturn(new PageImpl<>(List.of(video), PageRequest.of(0, 24), 1));

        mockMvc.perform(get(url).servletPath(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(5));

        when(collectionService.getVideos(ID, 1, 24)).thenThrow(error(HttpStatus.NOT_FOUND));
        mockMvc.perform(get(url).servletPath(url).param("page", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns201() throws Exception {
        when(collectionService.create(any())).thenReturn(dto("Hài", false));

        mockMvc.perform(post(BASE).servletPath(BASE).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hài\",\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Hài"));

        verify(collectionService).create(argThat(r -> "Hài".equals(r.getName())
                && r.getVisibility() == CollectionVisibility.PUBLIC));
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post(BASE).servletPath(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verify(collectionService, never()).create(any());
    }

    @Test
    void create_nameTooLong_returns400() throws Exception {
        mockMvc.perform(post(BASE).servletPath(BASE).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(CollectionRequest.MAX_NAME + 1) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(collectionService, never()).create(any());
    }

    @Test
    void create_serviceErrors_mapToStatus() throws Exception {
        when(collectionService.create(any())).thenThrow(error(HttpStatus.FORBIDDEN));
        mockMvc.perform(post(BASE).servletPath(BASE).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_duplicateName_returns409() throws Exception {
        String url = BASE + "/" + ID;
        when(collectionService.update(eq(ID), any())).thenThrow(error(HttpStatus.CONFLICT));

        mockMvc.perform(patch(url).servletPath(url).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"dup\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_returns204() throws Exception {
        String url = BASE + "/" + ID;
        mockMvc.perform(delete(url).servletPath(url).header("Authorization", TOKEN))
                .andExpect(status().isNoContent());
        verify(collectionService).delete(ID);
    }

    @Test
    void addAndRemoveVideo_return204() throws Exception {
        String url = BASE + "/" + ID + "/videos/9";
        mockMvc.perform(put(url).servletPath(url).header("Authorization", TOKEN))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(url).servletPath(url).header("Authorization", TOKEN))
                .andExpect(status().isNoContent());
        verify(collectionService).addVideo(ID, 9L);
        verify(collectionService).removeVideo(ID, 9L);
    }

    @Test
    void addVideo_withoutToken_returns401() throws Exception {
        String url = BASE + "/" + ID + "/videos/9";
        mockMvc.perform(put(url).servletPath(url)).andExpect(status().isUnauthorized());
        verify(collectionService, never()).addVideo(any(), any());
    }

    @Test
    void membership_returnsIds() throws Exception {
        String url = BASE + "/membership";
        when(collectionService.getMembership(9L)).thenReturn(List.of(ID));

        mockMvc.perform(get(url).servletPath(url).param("videoId", "9").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value(ID.toString()));
    }

    @Test
    void videoIds_returnsIds() throws Exception {
        String url = BASE + "/" + ID + "/video-ids";
        when(collectionService.getVideoIds(ID)).thenReturn(List.of(5L, 6L));

        mockMvc.perform(get(url).servletPath(url).header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1]").value(6));
    }

    @Test
    void videoIds_withoutToken_returns401() throws Exception {
        String url = BASE + "/" + ID + "/video-ids";
        mockMvc.perform(get(url).servletPath(url)).andExpect(status().isUnauthorized());
    }
}
