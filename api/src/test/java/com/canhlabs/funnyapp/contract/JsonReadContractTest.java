package com.canhlabs.funnyapp.contract;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Request-body leniency of the Boot 3.5 mapper that clients rely on (spec spring-boot-4-upgrade Rule 4, EC-4..EC-6).
 * Jackson 3 defaults differ, so SB4-4 must restore these settings explicitly.
 */
class JsonReadContractTest {

    private static final JsonMapper MAPPER = JsonContractSupport.bootMapper();

    /** Test-local DTO with real primitives: no current request DTO has a non-ignored primitive field. */
    public static class Body {
        public int count = 7;
        public long size = 7L;
        public boolean flag = true;
    }

    @Test
    void nullForPrimitive_deserializesToDefault() throws Exception {
        Body body = MAPPER.readValue("{\"count\":null,\"size\":null,\"flag\":null}", Body.class);

        assertThat(body.count).isZero();
        assertThat(body.size).isZero();
        assertThat(body.flag).isFalse();
    }

    @Test
    void unknownProperty_isIgnored() throws Exception {
        Body body = MAPPER.readValue("{\"count\":3,\"somethingNew\":\"x\"}", Body.class);

        assertThat(body.count).isEqualTo(3);
    }

    @Test
    void trailingTokens_throw() {
        assertThatThrownBy(() -> MAPPER.readValue("{\"count\":3} {\"count\":4}", Body.class))
                .isInstanceOf(Exception.class);
    }
}
