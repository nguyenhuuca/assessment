package com.canhlabs.funnyapp.share;

import com.canhlabs.funnyapp.config.AppProperties;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpMethod;

import java.util.List;

import static com.canhlabs.funnyapp.share.AppConstant.API.BASE_URL;

/**
 * Hold all constant for application
 */
public class AppConstant {

    public static final String CACHE_DIR = "video-cache/";
    public static final long CACHE_SIZE = 20 * 1024 * 1024; // 5MB
    public  static final long TOLERANCE_BYTES = 5 * 1024;  // 5k

    private AppConstant() {
    }


    public static class API {
        private API() {
        }
        public static final String BASE_URL = "/v1/funny-app";
        public static final String TAG_API = "Funny App API";
    }

    // inner class hold all the path url that ignore the security when access to system
    public static final class WebIgnoringConfig {
        private WebIgnoringConfig() {
        }
        public static final List<PathObject> WHITE_LIST_PATH = List.of(
                PathObject.builder().path("/user/join").method(HttpMethod.POST.name()).build(),
                PathObject.builder().path("/user/verify-magic").method(HttpMethod.GET.name()).build(),
                PathObject.builder().path("/user/mfa/verify").method(HttpMethod.POST.name()).build(),
                PathObject.builder().path("/share-links").method(HttpMethod.GET.name()).build(),
                PathObject.builder().basePath("").path("/actuator/health").method(HttpMethod.GET.name()).build(),
                PathObject.builder().basePath("").path("/actuator/info").method(HttpMethod.GET.name()).build(),
                PathObject.builder().path("/thread/**").method(HttpMethod.GET.name()).build()

        );

        // apply for document swagger
        public static final List<String> SWAGGER_DOC = List.of(
                        "/",
                        "/v1/funny-app/video-stream",
                        "/v1/funny-app/video-stream/**",
                        "/v3/api-docs/**", "/swagger-resources", "/swagger-resources/**","/swagger-ui/index.html",
                        "/configuration/ui", "/configuration/security", "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/webjars/**",
                        "/actuator/**",
                        "/v1/funny-app/top-videos",
                        "/v1/funny-app/top-videos/**"
                        // other public endpoints of your API may be appended to this array
        );

    }



    @Getter
    @Builder
    public static class PathObject {
        @Builder.Default
        String basePath = BASE_URL;
        String path;
        String method;
        public String getFullPath() {
            return basePath + path;
        }

    }

}
