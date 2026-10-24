package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.service.FfmpegService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class FfmpegServiceImpl implements FfmpegService {

    static final long TIMEOUT_SECONDS = 60;

    // One ffmpeg at a time: the regenerate loop would otherwise start one per file on a 1 vCPU VM
    private final Semaphore ffmpegSlot = new Semaphore(1, true);

    @Async
    @Override
    public void generateThumbnail(String videoPath, String thumbnailPath) {
        boolean acquired = false;
        try {
            ffmpegSlot.acquire();
            acquired = true;

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

            // Output is never read, so discard it: a full pipe would block ffmpeg forever
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                log.error("FFmpeg timed out after {}s for {}, process killed", TIMEOUT_SECONDS, videoPath);
                return;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("FFmpeg failed to generate thumbnail for {}. Exit code: {}", videoPath, exitCode);
                return;
            }

            log.info("Thumbnail generated at {}", thumbnailPath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thumbnail generation interrupted for {}", videoPath);
        } catch (Exception e) {
            log.error("Failed to generate thumbnail for {}: {}", videoPath, e.getMessage(), e);
        } finally {
            if (acquired) {
                ffmpegSlot.release();
            }
        }

    }

    int availableSlots() {
        return ffmpegSlot.availablePermits();
    }

}
