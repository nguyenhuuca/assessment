package com.canhlabs.funnyapp.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "cache")
@Component
@Getter
@Setter
public class CacheProperties {
    private String type;
    private Mfa mfa = new Mfa();
    private DefaultSetting defaultSettings = new DefaultSetting();

    @Getter
    @Setter
    public static class Mfa {
        private long ttlMinutes;
        private long maxSize;
    }

    @Getter
    @Setter
    public static class DefaultSetting {
        private long ttlMinutes;
        private long maxSize;
    }
}
