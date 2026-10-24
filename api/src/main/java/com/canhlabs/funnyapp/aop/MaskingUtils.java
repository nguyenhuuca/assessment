package com.canhlabs.funnyapp.aop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MaskingUtils {

    private static final String MASK = "***MASKED***";
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

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
            return MAPPER.readValue(MAPPER.writeValueAsString(obj), obj.getClass());
        } catch (Exception e) {
            return obj;
        }
    }

    /**
     * Serializes to JSON with every configured sensitive key masked at any depth
     * (e.g. ResponseEntity → body → data → jwt), not just the top-level fields.
     */
    public String toJsonSafe(Object obj) {
        try {
            JsonNode node = MAPPER.valueToTree(obj);
            maskTree(node);
            return MAPPER.writeValueAsString(node);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            // Never fall back to toString(): it could print the very values we mask
            return "<unserializable " + (obj == null ? "null" : obj.getClass().getSimpleName()) + ">";
        }
    }

    private void maskTree(JsonNode node) {
        if (node instanceof ObjectNode object) {
            Set<String> sensitive = sensitiveKeys();
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = object.get(name);
                if (sensitive.contains(name.toLowerCase(Locale.ROOT)) && !child.isNull()) {
                    object.put(name, MASK);
                } else {
                    maskTree(child);
                }
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(this::maskTree);
        }
    }

    private Set<String> sensitiveKeys() {
        return maskingProperties.getFields().stream()
                .map(f -> f.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
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
