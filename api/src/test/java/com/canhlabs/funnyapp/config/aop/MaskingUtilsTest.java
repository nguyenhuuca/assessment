package com.canhlabs.funnyapp.config.aop;
import com.canhlabs.funnyapp.aop.MaskingProperties;
import com.canhlabs.funnyapp.aop.MaskingUtils;
import com.canhlabs.funnyapp.aop.Sensitive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MaskingUtilsTest {

    private MaskingProperties maskingProperties;
    private MaskingUtils maskingUtils;

    static class TestObj {
        @Sensitive
        public String secret = "mySecret";
        public String normal = "normalValue";
        public String password = "myPassword";
    }

    @BeforeEach
    void setUp() {
        maskingProperties = mock(MaskingProperties.class);
        when(maskingProperties.getFields()).thenReturn(List.of("password"));
        maskingUtils = new MaskingUtils(maskingProperties);
    }

    @Test
    void maskSensitiveFields_shouldMaskAnnotatedAndConfiguredFields() {
        TestObj obj = new TestObj();
        TestObj masked = (TestObj) maskingUtils.maskSensitiveFields(obj);

        assertThat(masked.secret).isEqualTo("***MASKED***");
        assertThat(masked.password).isEqualTo("***MASKED***");
        assertThat(masked.normal).isEqualTo("normalValue");
    }

    @Test
    void toJsonSafe_masksNestedSensitiveKeysAtAnyDepth() {
        when(maskingProperties.getFields()).thenReturn(List.of("jwt", "sessionToken"));
        var user = new java.util.LinkedHashMap<String, Object>();
        user.put("email", "a@test.com");
        user.put("jwt", "eyJ.secret.sig");
        var body = java.util.Map.of("data", user, "items", List.of(java.util.Map.of("SessionToken", "s3cr3t")));
        var response = org.springframework.http.ResponseEntity.ok(body);

        String json = maskingUtils.toJsonSafe(response);

        assertThat(json).doesNotContain("eyJ.secret.sig").doesNotContain("s3cr3t")
                .contains("\"jwt\":\"***MASKED***\"")
                .contains("\"SessionToken\":\"***MASKED***\"")
                .contains("a@test.com");
    }

    @Test
    void toJsonSafe_keepsNullSensitiveValuesAndNeverFallsBackToToString() {
        when(maskingProperties.getFields()).thenReturn(List.of("jwt"));
        assertThat(maskingUtils.toJsonSafe(java.util.Collections.singletonMap("jwt", null))).isEqualTo("{\"jwt\":null}");

        Object unserializable = new Object() {
            final Object self = this; // cycle → serialization fails
            @Override public String toString() { return "jwt=eyJ.leak"; }
        };
        assertThat(maskingUtils.toJsonSafe(unserializable)).doesNotContain("eyJ.leak");
    }

    @Test
    void maskSensitiveFields_shouldReturnNullForNullInput() {
        assertThat(maskingUtils.maskSensitiveFields(null)).isNull();
    }

    @Test
    void maskAll_shouldReturnAllStars() {
        assertThat(MaskingUtils.maskAll("abc")).isEqualTo("***");
        assertThat(MaskingUtils.maskAll("")).isEqualTo("");
        assertThat(MaskingUtils.maskAll(null)).isEqualTo("");
    }

    @Test
    void maskHalfMiddle_shouldMaskMiddle() {
        assertThat(MaskingUtils.maskHalfMiddle("12345678")).isEqualTo("12****78");
        assertThat(MaskingUtils.maskHalfMiddle("ab")).isEqualTo("**");
        assertThat(MaskingUtils.maskHalfMiddle("a")).isEqualTo("*");
        assertThat(MaskingUtils.maskHalfMiddle("")).isEqualTo("");
        assertThat(MaskingUtils.maskHalfMiddle(null)).isNull();
    }

    @Test
    void maskFieldValue_shouldMaskPasswordFully() {
        assertThat(MaskingUtils.maskFieldValue("password", "secret")).isEqualTo("******");
        assertThat(MaskingUtils.maskFieldValue("email", "abcdef")).isEqualTo("a****f");
        assertThat(MaskingUtils.maskFieldValue("password", null)).isNull();
    }

    @Test
    void toJsonSafe_shouldReturnJsonString() {
        TestObj obj = new TestObj();
        String json = maskingUtils.toJsonSafe(obj);
        assertThat(json).contains("mySecret");
    }
}