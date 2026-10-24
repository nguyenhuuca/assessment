package com.canhlabs.funnyapp.service.impl;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HlsStorageTest {

    @TempDir
    Path temp;

    private HlsStorage storage;

    @BeforeEach
    void setUp() {
        storage = new HlsStorage(temp.resolve("hls"));
    }

    private void touch(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }

    private Path symlinkOrSkip(Path link, Path target) {
        try {
            Files.createDirectories(link.getParent());
            return Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assumptions.abort("symbolic links are not permitted here: " + e.getMessage());
            return null;
        }
    }

    @Test
    void resolveSegmentAndPlaylist_symlinkedFile_isTreatedAsNotFound() throws IOException {
        Path outside = temp.resolve("secret.ts");
        Files.writeString(outside, "secret");
        symlinkOrSkip(storage.dir("abc").resolve("seg_0.ts"), outside);
        symlinkOrSkip(storage.dir("abc").resolve("playlist.m3u8"), outside);

        assertThat(storage.resolveSegment("abc", "seg_0.ts")).doesNotExist();
        assertThat(storage.resolvePlaylist("abc")).doesNotExist();
    }

    @Test
    void resolveSegment_symlinkedRenditionDir_isTreatedAsNotFound() throws IOException {
        Path outsideDir = temp.resolve("elsewhere");
        touch(outsideDir.resolve("seg_0.ts"));
        symlinkOrSkip(storage.dir("abc"), outsideDir);

        assertThat(storage.resolveSegment("abc", "seg_0.ts")).doesNotExist();
    }

    @Test
    void resolveSegment_regularFile_isStillReturned() throws IOException {
        touch(storage.dir("abc").resolve("seg_0.ts"));

        assertThat(storage.resolveSegment("abc", "seg_0.ts")).exists();
    }

    @Test
    void defaultRoot_isCacheDirHls() {
        assertThat(new HlsStorage().root()).isEqualTo(Path.of("video-cache", "hls").toAbsolutePath().normalize());
    }

    @Test
    void paths_liveUnderTheRoot() {
        Path root = storage.root();

        assertThat(storage.dir("abc_-1")).isEqualTo(root.resolve("abc_-1"));
        assertThat(storage.tmpDir("abc_-1")).isEqualTo(root.resolve("abc_-1.tmp"));
        assertThat(storage.resolvePlaylist("abc_-1")).isEqualTo(root.resolve("abc_-1").resolve("playlist.m3u8"));
        assertThat(storage.resolveSegment("abc_-1", "seg_12.ts")).isEqualTo(root.resolve("abc_-1").resolve("seg_12.ts"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", ".", "../x", "a/b", "a\\b", "%2F", "..%2Fx", "/etc/passwd", "C:\\Windows", "a.b", "a b",
            "", "a\n", "a\u0000b"})
    void badFileIds_areRejectedEverywhere(String fileId) {
        assertThat(HlsStorage.isValidFileId(fileId)).isFalse();
        assertThatThrownBy(() -> storage.dir(fileId)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.tmpDir(fileId)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.resolvePlaylist(fileId)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.resolveSegment(fileId, "seg_1.ts")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete(fileId)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fileIdLength_isCappedAt128() {
        assertThat(HlsStorage.isValidFileId("a".repeat(128))).isTrue();
        assertThat(HlsStorage.isValidFileId("a".repeat(129))).isFalse();
        assertThat(HlsStorage.isValidFileId(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../x.ts", "seg_1.m3u8", "seg_.ts", "seg_123456.ts", "seg_1.ts/", "/seg_1.ts", "seg_1.ts\n",
            "seg_%2F1.ts", "playlist.m3u8", "SEG_1.ts", "seg_1.TS", "seg_-1.ts", ""})
    void badSegments_areRejected(String segment) {
        assertThat(HlsStorage.isValidSegment(segment)).isFalse();
        assertThatThrownBy(() -> storage.resolveSegment("abc", segment)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void segmentNames_acceptOneToFiveDigits() {
        assertThat(HlsStorage.isValidSegment("seg_0.ts")).isTrue();
        assertThat(HlsStorage.isValidSegment("seg_99999.ts")).isTrue();
        assertThat(HlsStorage.isValidSegment(null)).isFalse();
    }

    @Test
    void publish_renamesTmpToFinalDirectory() throws IOException {
        touch(storage.tmpDir("vid1").resolve("playlist.m3u8"));
        touch(storage.tmpDir("vid1").resolve("seg_0.ts"));

        storage.publish("vid1");

        assertThat(storage.exists("vid1")).isTrue();
        assertThat(Files.exists(storage.tmpDir("vid1"))).isFalse();
        assertThat(storage.resolvePlaylist("vid1")).exists();
        assertThat(storage.resolveSegment("vid1", "seg_0.ts")).exists();
    }

    @Test
    void publish_neverReplacesAnExistingRendition() throws IOException {
        touch(storage.dir("vid1").resolve("playlist.m3u8"));
        touch(storage.tmpDir("vid1").resolve("playlist.m3u8"));

        assertThatThrownBy(() -> storage.publish("vid1")).isInstanceOf(UncheckedIOException.class);

        assertThat(storage.tmpDir("vid1")).exists();
    }

    @Test
    void publish_withoutTmp_fails() {
        assertThatThrownBy(() -> storage.publish("vid1")).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void delete_removesTheTreeAndToleratesMissing() throws IOException {
        touch(storage.dir("vid1").resolve("playlist.m3u8"));
        touch(storage.dir("vid1").resolve("seg_0.ts"));

        storage.delete("vid1");
        storage.delete("vid1");
        storage.delete("never-existed");

        assertThat(storage.dir("vid1")).doesNotExist();
    }

    @Test
    void deleteTmp_removesOnlyTheWorkDirectory() throws IOException {
        touch(storage.tmpDir("vid1").resolve("seg_0.ts"));
        touch(storage.dir("vid1").resolve("playlist.m3u8"));

        storage.deleteTmp("vid1");

        assertThat(storage.tmpDir("vid1")).doesNotExist();
        assertThat(storage.dir("vid1")).exists();
    }

    @Test
    void deleteTmpDirs_sweepsEveryTmpDirectoryOnly() throws IOException {
        touch(storage.tmpDir("a").resolve("seg_0.ts"));
        touch(storage.tmpDir("b").resolve("seg_0.ts"));
        touch(storage.dir("c").resolve("playlist.m3u8"));

        storage.deleteTmpDirs();

        assertThat(storage.tmpDir("a")).doesNotExist();
        assertThat(storage.tmpDir("b")).doesNotExist();
        assertThat(storage.dir("c")).exists();
    }

    @Test
    void listRenditionIds_returnsPublishedDirectoriesOnly() throws IOException {
        touch(storage.dir("one").resolve("playlist.m3u8"));
        touch(storage.dir("two").resolve("playlist.m3u8"));
        touch(storage.tmpDir("three").resolve("seg_0.ts"));
        touch(storage.root().resolve("stray-file"));
        Files.createDirectories(storage.root().resolve("bad name"));

        assertThat(storage.listRenditionIds()).containsExactlyInAnyOrder("one", "two");
    }

    @Test
    void listAndSweep_onMissingRoot_areEmpty() {
        assertThat(storage.listRenditionIds()).isEmpty();
        storage.deleteTmpDirs();
    }

    @Test
    void usableSpace_isPositiveAndCreatesTheRoot() {
        assertThat(storage.usableSpace()).isPositive();
        assertThat(storage.root()).isDirectory();
    }
}
