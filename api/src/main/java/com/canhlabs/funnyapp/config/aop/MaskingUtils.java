package com.canhlabs.funnyapp.config.aop;

import com.canhlabs.funnyapp.config.MaskingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
public class MaskingUtils {

    private final MaskingProperties maskingProperties;

    public MaskingUtils(MaskingProperties maskingProperties) {
        this.maskingProperties = maskingProperties;
    }

    public Object maskSensitiveFields(Object obj) {
        if (obj == null) return null;

        try {
            Class<?> clazz = obj.getClass();
            Object clone = cloneObject(obj);
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);

                boolean isAnnotated = field.isAnnotationPresent(Sensitive.class);
                boolean isInConfiguredList = maskingProperties.getFields().contains(field.getName());

                if (isAnnotated || isInConfiguredList) {
                    field.set(clone, "***MASKED***");
                }
            }
            return clone;
        } catch (Exception e) {
            return obj;
        }
    }

    private Object cloneObject(Object obj) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(mapper.writeValueAsString(obj), obj.getClass());
        } catch (Exception e) {
            return obj;
        }
    }

    public String toJsonSafe(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return String.valueOf(obj);
        }
    }
}
