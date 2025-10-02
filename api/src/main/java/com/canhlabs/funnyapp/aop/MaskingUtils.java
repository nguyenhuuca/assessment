package com.canhlabs.funnyapp.aop;

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

    /**
     * Masks sensitive fields in the given object.
     * Fields are considered sensitive if they are annotated with @Sensitive
     * or if their names are included in the configured list of sensitive fields.
     * Masking is done by replacing the field value with "***MASKED***".
     *
     * @param obj The object to mask.
     * @return A new object with sensitive fields masked, or the original object if an error occurs.
     */
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

    /** Mask the middle half of the input string, leaving the first and last quarters unmasked
     * Examples:
     * - "12345678" -> "12****78"
     * - "abcdef" -> "ab**ef"
     * - "ab" -> "**"
     * - "a" -> "*"
     * - "" -> ""
     * - null -> null
     */
    public static String maskHalfMiddle(String input) {
        if (input == null || input.isEmpty()) return input;

        int length = input.length();
        if (length <= 2) return "*".repeat(length);

        int unmaskedLen = length / 4;
        int end = length - unmaskedLen;

        return input.substring(0, unmaskedLen) +
                "*".repeat(end - unmaskedLen) +
                input.substring(end);
    }
    // Mask all characters in the input string
    public static String maskAll(String input) {
        return "*".repeat(input != null ? input.length() : 0);
    }

    // Mask the value if the field name indicates it's sensitive (e.g., "password")
    public static String maskFieldValue(String fieldName, String value) {
        if (value == null) return null;
        return fieldName.equalsIgnoreCase("password")
                ? maskAll(value)
                : maskHalfMiddle(value);
    }
}
