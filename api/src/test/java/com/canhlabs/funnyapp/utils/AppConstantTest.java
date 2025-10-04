package com.canhlabs.funnyapp.utils;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.junit.jupiter.api.Assertions.*;

class AppConstantTest {
    @Test
    void testConstants() {
        assertEquals("video-cache/", AppConstant.CACHE_DIR);
        assertEquals(20 * 1024 * 1024, AppConstant.CACHE_SIZE);
        assertEquals(5 * 1024, AppConstant.TOLERANCE_BYTES);
        assertEquals("1uk7TUSvUkE9if6HYnY4ap2Kj0gSZ5qlz", AppConstant.FOLDER_ID);
        assertEquals("/v1/funny-app", AppConstant.API.BASE_URL);
        assertEquals("Funny App API", AppConstant.API.TAG_API);
    }

    @Test
    void testWebIgnoringConfigWhiteListPath() {
        assertFalse(AppConstant.WebIgnoringConfig.WHITE_LIST_PATH.isEmpty());
        assertEquals("/user/join", AppConstant.WebIgnoringConfig.WHITE_LIST_PATH.get(0).getPath());
        assertEquals(HttpMethod.POST.name(), AppConstant.WebIgnoringConfig.WHITE_LIST_PATH.get(0).getMethod());
    }

    @Test
    void testSwaggerDocList() {
        assertTrue(AppConstant.WebIgnoringConfig.ALLOW_ALL_METHOD.contains("/swagger-ui.html"));
        assertTrue(AppConstant.WebIgnoringConfig.ALLOW_ALL_METHOD.contains("/v3/api-docs/**"));
    }

    @Test
    void testPathObjectBuilderAndGetFullPath() {
        AppConstant.PathObject obj = AppConstant.PathObject.builder()
                .path("/test-path")
                .method(HttpMethod.GET.name())
                .build();
        assertEquals(AppConstant.API.BASE_URL + "/test-path", obj.getFullPath());
        assertEquals(HttpMethod.GET.name(), obj.getMethod());
        assertEquals(AppConstant.API.BASE_URL, obj.getBasePath());
    }

    @Test
    void testPathObjectCustomBasePath() {
        AppConstant.PathObject obj = AppConstant.PathObject.builder()
                .basePath("")
                .path("/custom-path")
                .method(HttpMethod.POST.name())
                .build();
        assertEquals("/custom-path", obj.getFullPath());
        assertEquals(HttpMethod.POST.name(), obj.getMethod());
        assertEquals("", obj.getBasePath());
    }
}
