package com.canhlabs.funnyapp.entity;

import com.canhlabs.funnyapp.enums.VideoStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VideoSourceDefaultsTest {

    /** status / priority / is_hide are NOT NULL columns: the builder must apply the field defaults. */
    @Test
    void builder_appliesNotNullDefaults() {
        VideoSource v = VideoSource.builder().videoId(1L).sourceType("google_drive").sourceId("f").build();
        assertThat(v.getStatus()).isEqualTo(VideoStatus.PUBLISHED);
        assertThat(v.getPriority()).isZero();
        assertThat(v.isHide()).isFalse();
    }
}
