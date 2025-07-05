package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.service.FfmpegService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FfmpegServiceImpl implements FfmpegService {

    @Async
    @Override
    public void generateThumbnail(String videoPath, String thumbnailPath) {
        try {
            // Using ffmpeg to generate a thumbnail from the video
            // Ensure ffmpeg is installed and available in the system PATH
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-ss", "00:00:01",   // snapshot at 1 second
                    "-i", videoPath,
                    "-frames:v", "1",
                    "-q:v", "7",
                    thumbnailPath
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg failed with exit code " + exitCode);
            }

            log.info("🖼️ Thumbnail generated at {}", thumbnailPath);
        } catch (Exception e) {
            log.error("❌ Failed to generate thumbnail for {}: {}", videoPath, e.getMessage());
            throw new RuntimeException("Failed to generate thumbnail", e);
        }

    }

}
