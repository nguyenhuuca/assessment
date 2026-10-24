package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.HlsErrorCode;
import com.canhlabs.funnyapp.enums.HlsStatus;
import com.canhlabs.funnyapp.utils.AppConstant;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the SQL text and the JPA mapping; the project has no database test harness. */
class VideoSourceHlsMigrationTest {

    private static String sql() throws Exception {
        return new ClassPathResource("db/changelog/sql/202610240001-video-source-hls.sql")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void migration_addsTwoColumnsWithSafeDefaults() throws Exception {
        String sql = sql();

        assertThat(sql).contains("ADD COLUMN hls_status VARCHAR(20) NOT NULL DEFAULT 'NONE'");
        assertThat(sql).contains("ADD COLUMN hls_error  VARCHAR(40)");
        assertThat(sql).doesNotContainIgnoringCase("DROP");
    }

    @Test
    void checkConstraint_matchesTheEnum() throws Exception {
        Matcher m = Pattern.compile("CHECK \\(hls_status IN \\(([^)]*)\\)\\)").matcher(sql());

        assertThat(m.find()).isTrue();
        List<String> values = Arrays.stream(m.group(1).split(",")).map(s -> s.trim().replace("'", "")).toList();
        assertThat(values).containsExactlyElementsOf(Arrays.stream(HlsStatus.values()).map(Enum::name).toList());
    }

    @Test
    void queueIndex_isPartialOverInFlightRows() throws Exception {
        assertThat(sql()).containsPattern("CREATE INDEX idx_video_sources_hls_queue\\s+ON video_sources \\(updated_at\\)\\s+"
                + "WHERE hls_status IN \\('PENDING', 'PROCESSING'\\)");
    }

    @Test
    void entity_mapsHlsColumnsThatExistInTheMigration() throws Exception {
        Field status = VideoSource.class.getDeclaredField("hlsStatus");
        Field error = VideoSource.class.getDeclaredField("hlsError");

        assertThat(status.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
        assertThat(status.getAnnotation(Column.class).name()).isEqualTo("hls_status");
        assertThat(error.getAnnotation(Column.class).name()).isEqualTo("hls_error");
        assertThat(sql()).contains("hls_status").contains("hls_error");
        assertThat(VideoSource.builder().build().getHlsStatus()).isEqualTo(HlsStatus.NONE);
        assertThat(VideoSource.builder().build().getHlsError()).isNull();
    }

    @Test
    void errorCodes_matchBr9AndFitTheColumn() {
        for (HlsErrorCode code : HlsErrorCode.values()) {
            assertThat(code.name().length()).isLessThanOrEqualTo(40);
        }
        assertThat(HlsErrorCode.values()).extracting(Enum::name).containsExactly("SOURCE_MISSING", "FFMPEG_NOT_FOUND",
                "UNSUPPORTED_CODEC", "DISK_FULL", "TIMEOUT", "FFMPEG_FAILED", "OUTPUT_MISSING");
    }

    @Test
    void claimHls_isAConditionalAtomicUpdateThatTouchesUpdatedAt() throws Exception {
        Method m = VideoSourceRepository.class.getMethod("claimHls", Long.class);
        String q = m.getAnnotation(Query.class).value();

        assertThat(m.getReturnType()).isEqualTo(int.class);
        assertThat(m.getAnnotation(Modifying.class)).isNotNull();
        assertThat(m.getAnnotation(Transactional.class)).isNotNull();
        assertThat(q).contains("hls_status = 'PENDING'").contains("SET hls_status = 'PROCESSING'")
                .contains("updated_at = now()").contains("id = :id");
    }

    @Test
    void markHlsReadyAndError_areConditionalOnProcessing() throws Exception {
        Method ready = VideoSourceRepository.class.getMethod("markHlsReady", Long.class);
        Method error = VideoSourceRepository.class.getMethod("markHlsError", Long.class, String.class);
        String readyQuery = ready.getAnnotation(Query.class).value();
        String errorQuery = error.getAnnotation(Query.class).value();

        for (Method m : new Method[]{ready, error}) {
            assertThat(m.getReturnType()).isEqualTo(int.class);
            assertThat(m.getAnnotation(Modifying.class)).isNotNull();
            assertThat(m.getAnnotation(Transactional.class)).isNotNull();
        }
        assertThat(readyQuery).contains("SET hls_status = 'READY'").contains("hls_error = NULL")
                .contains("updated_at = now()").contains("id = :id AND hls_status = 'PROCESSING'");
        assertThat(errorQuery).contains("SET hls_status = 'ERROR'").contains("hls_error = :code")
                .contains("updated_at = now()").contains("id = :id AND hls_status = 'PROCESSING'");
    }

    @Test
    void resetAndQueue_queriesMatchTheStateMachine() throws Exception {
        Method reset = VideoSourceRepository.class.getMethod("resetHlsProcessing");
        Method queue = VideoSourceRepository.class.getMethod("findHlsQueue");

        assertThat(reset.getAnnotation(Query.class).value())
                .contains("SET hls_status = 'PENDING'").contains("WHERE hls_status = 'PROCESSING'")
                .contains("updated_at = now()");
        assertThat(queue.getAnnotation(Query.class).value())
                .contains("hls_status = 'PENDING'").contains("ORDER BY updated_at, id");
        assertThat(VideoSourceRepository.class.getMethod("findAllByHlsStatus", HlsStatus.class)).isNotNull();
    }

    @Test
    void flagName_isTheHarnessKey() {
        assertThat(AppConstant.Flags.HLS_STREAMING).isEqualTo("hls_streaming_enabled");
    }
}
