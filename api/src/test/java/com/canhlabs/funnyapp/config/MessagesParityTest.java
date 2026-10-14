package com.canhlabs.funnyapp.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards ADR-0020 D4: both bundles expose the same keys and none is blank. */
class MessagesParityTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

    private static Properties load(String name) throws IOException {
        try (Reader reader = new InputStreamReader(
                MessagesParityTest.class.getResourceAsStream("/i18n/" + name), StandardCharsets.UTF_8)) {
            Properties p = new Properties();
            p.load(reader);
            return p;
        }
    }

    @Test
    void englishAndVietnameseHaveIdenticalKeys() throws IOException {
        Properties en = load("messages.properties");
        Properties vi = load("messages_vi.properties");

        assertThat(en).isNotEmpty();
        assertThat(new TreeSet<>(vi.stringPropertyNames())).isEqualTo(new TreeSet<>(en.stringPropertyNames()));
    }

    @Test
    void noValueIsBlank() throws IOException {
        for (String file : new String[]{"messages.properties", "messages_vi.properties"}) {
            Properties p = load(file);
            p.forEach((k, v) -> assertThat(String.valueOf(v)).as("%s -> %s", file, k).isNotBlank());
        }
    }

    @Test
    void placeholdersMatchBetweenLanguages() throws IOException {
        Properties en = load("messages.properties");
        Properties vi = load("messages_vi.properties");
        for (String key : en.stringPropertyNames()) {
            assertThat(placeholders(vi.getProperty(key))).as(key).isEqualTo(placeholders(en.getProperty(key)));
        }
    }

    private static TreeSet<String> placeholders(String value) {
        TreeSet<String> set = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(value);
        while (m.find()) {
            set.add(m.group());
        }
        return set;
    }
}
