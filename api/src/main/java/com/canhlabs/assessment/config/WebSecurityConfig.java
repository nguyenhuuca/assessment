package com.canhlabs.assessment.config;

import com.canhlabs.assessment.share.AppConstant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    public void configure(WebSecurity web) {
        AppConstant.WebIgnoringConfig.WHITE_LIST_PATH.forEach(item -> web.ignoring().antMatchers(HttpMethod.valueOf(item.getMethod()), item.getFullPath()));
    }


    @SuppressWarnings("squid:S5122")
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.cors().configurationSource(request -> new CorsConfiguration().applyPermitDefaultValues());
        // add white list urls for swagger ui
        http
                .authorizeRequests()
                .antMatchers(AppConstant.WebIgnoringConfig.SWAGGER_DOC.toArray(new String[0])).permitAll()
                .anyRequest().permitAll()
                .and()
                .csrf().disable()
                ;

        // set session stateless policy
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }

    @Bean(name = BeanIds.AUTHENTICATION_MANAGER)
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }


}
