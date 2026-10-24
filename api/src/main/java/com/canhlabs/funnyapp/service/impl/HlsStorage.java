package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.utils.AppConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Paths and file operations for HLS renditions under {@code video-cache/hls/} (ADR-0031).
 * Every id and segment name is validated and every resolved path is proven to stay inside its rendition
 * directory before it is returned (CWE-22). Invalid input throws {@link IllegalArgumentException} (HTTP 400).
 */
@Slf4j
@Component
public class HlsStorage {

    public static final String PLAYLIST = "playlist.m3u8";
    public static final String SEGMENT_PATTERN = "seg_%d.ts";
    static final String TMP_SUFFIX = ".tmp";
    private static final Pattern FILE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");
    private static final Pattern SEGMENT = Pattern.compile("^seg_\\d{1,5}\\.ts$");

    private final Path root;

    @Autowired
    public HlsStorage() {
        this(Path.of(AppConstant.CACHE_DIR, "hls"));
    }

    /** Tests point the root at a temp directory. */
    HlsStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public static boolean isValidFileId(String fileId) {
        return fileId != null && FILE_ID.matcher(fileId).matches();
    }

    public static boolean isValidSegment(String segment) {
        return segment != null && SEGMENT.matcher(segment).matches();
    }

    public Path root() {
        return root;
    }

    /** Published rendition directory {@code hls/{fileId}/}. */
    public Path dir(String fileId) {
        requireFileId(fileId);
        return inside(root, fileId);
    }

    /** Work directory {@code hls/{fileId}.tmp/} that FFmpeg writes into. */
    public Path tmpDir(String fileId) {
        requireFileId(fileId);
        return inside(root, fileId + TMP_SUFFIX);
    }

    public Path resolvePlaylist(String fileId) {
        return rejectSymlink(fileId, inside(dir(fileId), PLAYLIST));
    }

    public Path resolveSegment(String fileId, String segment) {
        if (!isValidSegment(segment)) {
            throw new IllegalArgumentException("Invalid segment name");
        }
        return rejectSymlink(fileId, inside(dir(fileId), segment));
    }

    /** A link in the rendition directory could point outside it; treat it as not found (the file will not exist). */
    private Path rejectSymlink(String fileId, Path target) {
        if (Files.isSymbolicLink(target) || Files.isSymbolicLink(dir(fileId))) {
            log.warn("Refusing symbolic link in HLS rendition {}", fileId);
            return root.resolve(".missing");
        }
        return target;
    }

    public boolean exists(String fileId) {
        return Files.isDirectory(dir(fileId));
    }

    /** Atomically renames {@code hls/{fileId}.tmp/} to {@code hls/{fileId}/}. Never replaces an existing directory. */
    public void publish(String fileId) {
        Path tmp = tmpDir(fileId);
        Path target = dir(fileId);
        try {
            if (Files.exists(target)) {
                throw new FileAlreadyExistsException(target.getFileName().toString());
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot publish HLS rendition " + fileId, e);
        }
    }

    /** Deletes {@code hls/{fileId}/} recursively; a missing directory is fine. */
    public void delete(String fileId) {
        deleteRecursively(dir(fileId));
    }

    /** Deletes {@code hls/{fileId}.tmp/} recursively; a missing directory is fine. */
    public void deleteTmp(String fileId) {
        deleteRecursively(tmpDir(fileId));
    }

    /** Free bytes on the file store that holds the HLS root; the root is created when missing. */
    public long usableSpace() {
        try {
            Files.createDirectories(root);
            return Files.getFileStore(root).getUsableSpace();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read free disk space", e);
        }
    }

    /** File ids of the published rendition directories (work directories are excluded). */
    public List<String> listRenditionIds() {
        return childDirNames().filter(n -> !n.endsWith(TMP_SUFFIX) && isValidFileId(n)).toList();
    }

    /** Startup sweep (BR-11): removes every leftover {@code *.tmp} work directory. */
    public void deleteTmpDirs() {
        childDirNames().filter(n -> n.endsWith(TMP_SUFFIX)).forEach(n -> deleteRecursively(inside(root, n)));
    }

    private Stream<String> childDirNames() {
        if (!Files.isDirectory(root)) {
            return Stream.empty();
        }
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(Files::isDirectory).map(p -> p.getFileName().toString()).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list HLS directory", e);
        }
    }

    private static void requireFileId(String fileId) {
        if (!isValidFileId(fileId)) {
            throw new IllegalArgumentException("Invalid file id");
        }
    }

    /** Resolves {@code child} under {@code base} and proves the normalized result is still below it. */
    private static Path inside(Path base, String child) {
        Path resolved = base.resolve(child).normalize();
        if (!resolved.startsWith(base) || resolved.equals(base)) {
            throw new IllegalArgumentException("Path escapes the HLS directory");
        }
        return resolved;
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete " + dir.getFileName(), e);
        }
    }
}
