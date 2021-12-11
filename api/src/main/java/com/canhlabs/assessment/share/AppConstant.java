package com.canhlabs.assessment.share;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpMethod;

import java.util.List;

import static com.canhlabs.assessment.share.AppConstant.API.BASE_URL;

/**
 * Hold all constant for application
 */
public class AppConstant {

    private AppConstant() {
    }

    // Common
    // the constant will get all configure from application properties
    // because properties can use for all class (int Spring context and not Spring context),
    // so we need create the constant to load it
    public static final AppProperties props = new AppProperties();

    public static class API {
        private API() {
        }
        public static final String BASE_URL = "/v1/assessment";
        public static final String TAG_API = "Assessment API";
    }

    // inner class hold all the path url that ignore the security when access to system
    public static final class WebIgnoringConfig {
        private WebIgnoringConfig() {
        }
        public static final List<PathObject> WHITE_LIST_PATH = List.of(
                PathObject.builder().path("/join").method(HttpMethod.POST.name()).build(),
                PathObject.builder().path("/send-info").method(HttpMethod.POST.name()).build(),
                PathObject.builder().path("/share-links").method(HttpMethod.GET.name()).build(),
                PathObject.builder().basePath("").path("/actuator/health").method(HttpMethod.GET.name()).build(),
                PathObject.builder().basePath("").path("/actuator/info").method(HttpMethod.GET.name()).build()

        );

        // apply for document swagger
        public static final List<String> SWAGGER_DOC = List.of(
                        "/",
                        // -- swagger ui
                        "/v2/api-docs", "/swagger-resources", "/swagger-resources/**",
                        "/configuration/ui", "/configuration/security", "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/webjars/**"
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
