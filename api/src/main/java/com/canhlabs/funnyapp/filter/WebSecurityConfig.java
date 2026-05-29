package com.canhlabs.funnyapp.filter;

import com.canhlabs.funnyapp.enums.Permission;
import com.canhlabs.funnyapp.utils.AppConstant;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;
import java.util.function.Supplier;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {

    JWTAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    public void injectJWTFilter(JWTAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Build a white list from your AppConstant config
        String[] swaggerWhiteList = AppConstant.WebIgnoringConfig.ALLOW_ALL_METHOD.toArray(new String[0]);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    // Whitelisted endpoints
                    auth.requestMatchers("/v1/funny-app/admin/**").hasRole("ADMIN");
                    AppConstant.WebIgnoringConfig.WHITE_LIST_PATH.forEach(item -> auth.requestMatchers(HttpMethod.valueOf(item.getMethod()), item.getFullPath()).permitAll());
                    auth.requestMatchers(swaggerWhiteList).permitAll();
                    auth.anyRequest().permitAll();

                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


    @Bean
    static AnnotationTemplateExpressionDefaults templateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }

    // Registers Permission enum constants (ADMIN, READ, …) as resolvable identifiers
    // in the SpEL evaluation context. Required because AnnotationTemplateExpressionDefaults
    // expands Permission.ADMIN to the bare name "ADMIN", which SpEL cannot resolve on its own.
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        return permissionAwareExpressionHandler();
    }

    public static MethodSecurityExpressionHandler permissionAwareExpressionHandler() {
        return new DefaultMethodSecurityExpressionHandler() {
            @Override
            public EvaluationContext createEvaluationContext(
                    Supplier<Authentication> authentication, MethodInvocation mi) {
                StandardEvaluationContext ctx =
                        (StandardEvaluationContext) super.createEvaluationContext(authentication, mi);
                ctx.addPropertyAccessor(new PermissionEnumPropertyAccessor());
                return ctx;
            }
        };
    }

    private static class PermissionEnumPropertyAccessor implements PropertyAccessor {
        @Override
        public Class<?>[] getSpecificTargetClasses() {
            return null; // apply to all objects (root object in SpEL)
        }

        @Override
        public boolean canRead(EvaluationContext ctx, Object target, String name) {
            try {
                Permission.valueOf(name);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        @Override
        public TypedValue read(EvaluationContext ctx, Object target, String name) {
            return new TypedValue(Permission.valueOf(name));
        }

        @Override
        public boolean canWrite(EvaluationContext ctx, Object target, String name) {
            return false;
        }

        @Override
        public void write(EvaluationContext ctx, Object target, String name, Object newValue) {}
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            CorsConfiguration config = new CorsConfiguration().applyPermitDefaultValues();
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("*"));
            config.setAllowedOriginPatterns(List.of(
                    "http://localhost:*",
                    "https://*.canh-labs.com",
                    "https://canh-labs.com"
            ));
            return config;
        };
    }


}
