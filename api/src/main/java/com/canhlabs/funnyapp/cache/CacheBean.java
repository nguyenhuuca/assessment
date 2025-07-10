package com.canhlabs.funnyapp.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheBean {
    @Bean
    public AppCache<String, String> mfaSessionCache(AppCacheFactory factory, CacheProperties props) {
        return factory.createCache( props.getMfa().getTtlMinutes(), props.getMfa().getMaxSize());
    }
}

