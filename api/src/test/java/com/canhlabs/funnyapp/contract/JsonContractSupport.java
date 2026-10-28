package com.canhlabs.funnyapp.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared helpers for the JSON contract tests (SB4-1): the mapper is the exact Spring Boot auto-configured one that
 * MVC uses, and fixtures under {@code json-contract/} pin the wire format recorded from Boot 3.5.
 */
public final class JsonContractSupport {

    private static final String FIXTURE_DIR = "json-contract/";

    private JsonContractSupport() {
    }

    /** The Boot auto-configured {@link ObjectMapper} (no {@code spring.jackson} overrides in application.yaml). */
    public static ObjectMapper bootMapper() {
        AtomicReference<ObjectMapper> holder = new AtomicReference<>();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(ctx -> holder.set(ctx.getBean(ObjectMapper.class)));
        return holder.get();
    }

    /** Reads {@code json-contract/<name>} from the test classpath. */
    public static String fixture(String name) {
        try (InputStream in = JsonContractSupport.class.getClassLoader().getResourceAsStream(FIXTURE_DIR + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture " + FIXTURE_DIR + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read fixture " + name, e);
        }
    }

    /**
     * Removes the non-deterministic text field at {@code pointer} (JSON Pointer of the parent object plus the field
     * name, e.g. {@code /error/timestamp}) after asserting it matches {@code format}; returns the remaining JSON.
     */
    public static String stripTimestamp(String json, String parentPointer, String field, String format) {
        try {
            JsonNode root = bootMapper().readTree(json);
            ObjectNode parent = (ObjectNode) root.at(parentPointer);
            assertThat(parent.get(field).asText()).matches(format);
            parent.remove(field);
            return root.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Invalid JSON", e);
        }
    }

    /** Strict comparison: same fields, same values, array order ignored; extra or missing fields fail. */
    public static void assertMatchesFixture(String fixtureName, String actualJson) {
        try {
            JSONAssert.assertEquals(fixture(fixtureName), actualJson, JSONCompareMode.NON_EXTENSIBLE);
        } catch (JSONException e) {
            throw new IllegalStateException("Invalid JSON for fixture " + fixtureName, e);
        }
    }
}
