package com.canhlabs.funnyapp.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FfmpegServiceImplTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger serviceLogger;

    @BeforeEach
    void attachAppender() {
        serviceLogger = (Logger) LoggerFactory.getLogger(FfmpegServiceImpl.class);
        appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        serviceLogger.detachAppender(appender);
    }

    private boolean logged(String fragment) {
        return appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains(fragment));
    }

    private static Process processFinishing(int exitCode) throws InterruptedException {
        Process process = Mockito.mock(Process.class);
        Mockito.when(process.waitFor(FfmpegServiceImpl.TIMEOUT_SECONDS, TimeUnit.SECONDS)).thenReturn(true);
        Mockito.when(process.exitValue()).thenReturn(exitCode);
        return process;
    }

    @Test
    void generateThumbnail_handlesExceptionGracefully() throws Exception {
        FfmpegServiceImpl service = new FfmpegServiceImpl();
        try (MockedConstruction<ProcessBuilder> mocked = Mockito.mockConstruction(ProcessBuilder.class,
                (mock, context) -> Mockito.when(mock.start()).thenThrow(new IOException("ffmpeg not found")))) {
            service.generateThumbnail("video.mp4", "thumb.jpg");
            // No exception should be thrown
        }
        assertThat(logged("Failed to generate thumbnail")).isTrue();
        assertThat(service.availableSlots()).isEqualTo(1);
    }

    @Test
    void generateThumbnail_successfulRun_discardsOutputAndLogsSuccess() throws Exception {
        FfmpegServiceImpl service = new FfmpegServiceImpl();
        Process mockProcess = processFinishing(0);
        try (MockedConstruction<ProcessBuilder> mocked = Mockito.mockConstruction(ProcessBuilder.class,
                (mock, context) -> Mockito.when(mock.start()).thenReturn(mockProcess))) {
            service.generateThumbnail("video.mp4", "thumb.jpg");

            ProcessBuilder pb = mocked.constructed().get(0);
            Mockito.verify(pb).redirectErrorStream(true);
            Mockito.verify(pb).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        }
        assertThat(logged("Thumbnail generated at thumb.jpg")).isTrue();
        assertThat(service.availableSlots()).isEqualTo(1);
    }

    @Test
    void generateThumbnail_failedRun_doesNotLogSuccess() throws Exception {
        FfmpegServiceImpl service = new FfmpegServiceImpl();
        Process mockProcess = processFinishing(1);
        try (MockedConstruction<ProcessBuilder> mocked = Mockito.mockConstruction(ProcessBuilder.class,
                (mock, context) -> Mockito.when(mock.start()).thenReturn(mockProcess))) {
            service.generateThumbnail("video.mp4", "thumb.jpg");
        }
        assertThat(logged("FFmpeg failed")).isTrue();
        assertThat(logged("Thumbnail generated")).isFalse();
    }

    @Test
    void generateThumbnail_timeout_killsProcessAndDoesNotLogSuccess() throws Exception {
        FfmpegServiceImpl service = new FfmpegServiceImpl();
        Process mockProcess = Mockito.mock(Process.class);
        Mockito.when(mockProcess.waitFor(FfmpegServiceImpl.TIMEOUT_SECONDS, TimeUnit.SECONDS)).thenReturn(false);
        try (MockedConstruction<ProcessBuilder> mocked = Mockito.mockConstruction(ProcessBuilder.class,
                (mock, context) -> Mockito.when(mock.start()).thenReturn(mockProcess))) {
            service.generateThumbnail("video.mp4", "thumb.jpg");
        }
        Mockito.verify(mockProcess).destroyForcibly();
        assertThat(logged("timed out")).isTrue();
        assertThat(logged("Thumbnail generated")).isFalse();
        assertThat(service.availableSlots()).isEqualTo(1);
    }

    @Test
    void generateThumbnail_waitInterrupted_releasesSlotAndKeepsInterruptFlag() throws Exception {
        FfmpegServiceImpl service = new FfmpegServiceImpl();
        Process mockProcess = Mockito.mock(Process.class);
        Mockito.when(mockProcess.waitFor(FfmpegServiceImpl.TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .thenThrow(new InterruptedException("stop"));
        try (MockedConstruction<ProcessBuilder> mocked = Mockito.mockConstruction(ProcessBuilder.class,
                (mock, context) -> Mockito.when(mock.start()).thenReturn(mockProcess))) {
            service.generateThumbnail("video.mp4", "thumb.jpg");
        }
        assertThat(Thread.interrupted()).isTrue();
        assertThat(service.availableSlots()).isEqualTo(1);
    }
}
