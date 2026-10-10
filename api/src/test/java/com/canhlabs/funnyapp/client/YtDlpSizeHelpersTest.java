package com.canhlabs.funnyapp.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class YtDlpSizeHelpersTest {

    @Test
    void parseSize_units() {
        assertThat(YtDlpClientImpl.parseSize("1G")).isEqualTo(1024L * 1024 * 1024);
        assertThat(YtDlpClientImpl.parseSize("500M")).isEqualTo(500L * 1024 * 1024);
        assertThat(YtDlpClientImpl.parseSize("2k")).isEqualTo(2048L);
        assertThat(YtDlpClientImpl.parseSize("123")).isEqualTo(123L);
        assertThat(YtDlpClientImpl.parseSize("1.5MiB")).isEqualTo((long) (1.5 * 1024 * 1024));
        assertThat(YtDlpClientImpl.parseSize("junk")).isZero();
        assertThat(YtDlpClientImpl.parseSize(null)).isZero();
    }

    @Test
    void dirSize_sumsNestedFiles(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("a.part"), new byte[100]);
        Files.createDirectories(dir.resolve("sub"));
        Files.write(dir.resolve("sub/b"), new byte[50]);
        assertThat(YtDlpClientImpl.dirSize(dir)).isEqualTo(150);
        assertThat(YtDlpClientImpl.dirSize(dir.resolve("missing"))).isZero();
    }
}
