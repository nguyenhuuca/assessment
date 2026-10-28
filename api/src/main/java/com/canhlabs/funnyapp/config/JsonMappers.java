package com.canhlabs.funnyapp.config;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Single source of the Jackson 3 settings that keep the Spring Boot 3.5 wire behavior (spec spring-boot-4-upgrade
 * Rules 3 and 4). The MVC mapper gets them through the {@code JsonMapperBuilderCustomizer} in {@link ConfigBean};
 * code outside Spring uses {@link #create()}.
 */
public final class JsonMappers {

    private JsonMappers() {
    }

    /** Applies the shared settings to {@code builder} and returns it. */
    public static JsonMapper.Builder configure(JsonMapper.Builder builder) {
        return builder
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                // Enums by name(), as on 3.5: without this HttpStatus in Error serialized as "404 NOT_FOUND".
                // Jackson 3 also sorts properties alphabetically; key order may change, meaning does not (Rule 2).
                .configure(EnumFeature.WRITE_ENUMS_USING_TO_STRING, false);
    }

    /** A new mapper with the shared settings, for static fields and tests without a Spring context. */
    public static JsonMapper create() {
        return configure(JsonMapper.builder()).build();
    }
}
