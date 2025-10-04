package com.canhlabs.funnyapp.filter;

import com.canhlabs.funnyapp.utils.AppConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

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
                    auth.requestMatchers("/admin/**").authenticated();
                    AppConstant.WebIgnoringConfig.WHITE_LIST_PATH.forEach(item -> auth.requestMatchers(HttpMethod.valueOf(item.getMethod()), item.getFullPath()).permitAll());
                    auth.requestMatchers(swaggerWhiteList).permitAll();
                    auth.anyRequest().permitAll();

                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
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
