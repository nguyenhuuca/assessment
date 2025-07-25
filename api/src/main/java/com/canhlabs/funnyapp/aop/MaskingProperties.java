package com.canhlabs.funnyapp.aop;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "logging.masking")
public class MaskingProperties {
    private List<String> fields = new ArrayList<>();
}

