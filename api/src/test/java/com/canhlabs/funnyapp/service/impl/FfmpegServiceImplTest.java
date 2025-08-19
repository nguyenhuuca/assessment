package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.service.FfmpegService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

class FfmpegServiceImplTest {
    @Test
    void generateThumbnail_handlesExceptionGracefully() throws Exception {
        FfmpegServiceImpl service = new FfmpegServiceImpl();
        try (MockedConstruction<ProcessBuilder> mocked = Mockito.mockConstruction(ProcessBuilder.class,
                (mock, context) -> Mockito.when(mock.start()).thenThrow(new IOException("ffmpeg not found")))) {
            service.generateThumbnail("video.mp4", "thumb.jpg");
            // No exception should be thrown
        }
    }

    @Test
    void generateThumbnail_successfulRun() throws Exception {
        FfmpegServiceImpl service = new FfmpegServiceImpl();
        Process mockProcess = Mockito.mock(Process.class);
        Mockito.when(mockProcess.waitFor()).thenReturn(0);
        try (MockedConstruction<ProcessBuilder> mocked = Mockito.mockConstruction(ProcessBuilder.class,
                (mock, context) -> {
                    Mockito.when(mock.start()).thenReturn(mockProcess);
                    Mockito.when(mock.redirectErrorStream(true)).thenReturn(mock);
                })) {
            service.generateThumbnail("video.mp4", "thumb.jpg");
            // Should log info about thumbnail generation
        }
    }

    @Test
    void generateThumbnail_failedRun() throws Exception {
        FfmpegServiceImpl service = new FfmpegServiceImpl();
        Process mockProcess = Mockito.mock(Process.class);
        Mockito.when(mockProcess.waitFor()).thenReturn(1);
        try (MockedConstruction<ProcessBuilder> mocked = Mockito.mockConstruction(ProcessBuilder.class,
                (mock, context) -> {
                    Mockito.when(mock.start()).thenReturn(mockProcess);
                    Mockito.when(mock.redirectErrorStream(true)).thenReturn(mock);
                })) {
            service.generateThumbnail("video.mp4", "thumb.jpg");
            // Should log error about failed thumbnail generation
        }
    }
}

