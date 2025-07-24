package com.canhlabs.funnyapp.startup;

import com.canhlabs.funnyapp.dto.Range;
import com.canhlabs.funnyapp.cache.ChunkIndexCache;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static com.canhlabs.funnyapp.utils.AppConstant.CACHE_DIR;

@Component
@Slf4j
public class Seed {
    @Autowired
    ChunkIndexCache chunkIndexCache;

    @PostConstruct
    public void preloadAll() {
        File root = new File(CACHE_DIR);
        if (!root.exists()) {
            log.warn("No cache dir: " + CACHE_DIR);
            return;
        }
        for (File dir : Objects.requireNonNull(root.listFiles(File::isDirectory))) {
            String fileId = dir.getName();
            Set<Range> ranges = new HashSet<>();
            for (File chunkFile : Objects.requireNonNull(dir.listFiles())) {
                String name = chunkFile.getName().replace(".cache", "");
                String[] parts = name.split("-");
                if (parts.length == 2) {
                    long start = Long.parseLong(parts[0]);
                    long end = Long.parseLong(parts[1]);
                    log.info("Preloading chunk for fileId: {}, range: {}-{}", fileId, start, end);
                    ranges.add(new Range(start, end));
                }
            }
            chunkIndexCache.preload(fileId, ranges);
        }
    }
}
