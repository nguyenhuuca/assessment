package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.config.AppProperties;
import com.canhlabs.funnyapp.service.backup.BackupErrorCode;
import com.canhlabs.funnyapp.service.backup.BackupException;
import com.canhlabs.funnyapp.service.backup.BackupFile;
import com.canhlabs.funnyapp.service.backup.BackupStep;
import com.canhlabs.funnyapp.service.backup.BackupTier;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriveBackupStoreTest {

    private static final String ROOT = "root-folder_1";
    private static final String FOLDER_Q = "'root-folder_1' in parents and name = 'daily' and mimeType = "
            + "'application/vnd.google-apps.folder' and trashed = false";

    @TempDir
    Path tmp;

    private Drive drive;
    private Drive.Files files;
    private Drive.Files.List list;
    private Drive.Files.Create folderCreate;
    private Drive.Files.Create fileCreate;
    private MediaHttpUploader mediaUploader;
    private AppProperties props;
    private Path dump;

    @BeforeEach
    void setUp() throws IOException {
        drive = mock(Drive.class);
        files = mock(Drive.Files.class);
        list = mock(Drive.Files.List.class, RETURNS_SELF);
        folderCreate = mock(Drive.Files.Create.class, RETURNS_SELF);
        fileCreate = mock(Drive.Files.Create.class, RETURNS_SELF);
        mediaUploader = mock(MediaHttpUploader.class);
        when(drive.files()).thenReturn(files);
        when(files.list()).thenReturn(list);
        when(files.create(any(File.class))).thenReturn(folderCreate);
        when(files.create(any(File.class), any(AbstractInputStreamContent.class))).thenReturn(fileCreate);
        when(fileCreate.getMediaHttpUploader()).thenReturn(mediaUploader);
        props = new AppProperties();
        props.getBackup().setFolderId(ROOT);
        dump = tmp.resolve("db.dump");
        Files.write(dump, new byte[500]);
    }

    private DriveBackupStore store() {
        return new DriveBackupStore(() -> drive, props);
    }

    private static FileList folders(String... ids) {
        return new FileList().setFiles(java.util.Arrays.stream(ids).map(i -> new File().setId(i)).toList());
    }

    private static GoogleJsonResponseException http(int status) {
        return new GoogleJsonResponseException(
                new HttpResponseException.Builder(status, "err", new HttpHeaders()), null);
    }

    @Test
    void upload_usesExistingTierFolderAndSetsMetadata() throws IOException {
        when(list.execute()).thenReturn(folders("tier-1"));
        when(fileCreate.execute()).thenReturn(new File().setId("file-1"));

        String id = store().upload(dump, "backup.dump", BackupTier.DAILY);

        assertThat(id).isEqualTo("file-1");
        verify(list).setQ(FOLDER_Q);
        verify(list).setOrderBy("createdTime");
        verify(list).setFields("files(id)");
        verify(files, never()).create(any(File.class));
        ArgumentCaptor<File> meta = ArgumentCaptor.forClass(File.class);
        ArgumentCaptor<AbstractInputStreamContent> content = ArgumentCaptor.forClass(AbstractInputStreamContent.class);
        verify(files).create(meta.capture(), content.capture());
        assertThat(meta.getValue().getName()).isEqualTo("backup.dump");
        assertThat(meta.getValue().getParents()).containsExactly("tier-1");
        assertThat(meta.getValue().getMimeType()).isEqualTo("application/octet-stream");
        assertThat(meta.getValue().getAppProperties())
                .containsEntry("backupApp", "funnyapp-db").containsEntry("backupTier", "daily");
        assertThat(content.getValue().getType()).isEqualTo("application/octet-stream");
        assertThat(content.getValue().getLength()).isEqualTo(500L);
        verify(fileCreate).setFields("id");
        verify(mediaUploader).setDirectUploadEnabled(false);
        verify(mediaUploader).setChunkSize(8 * 1024 * 1024);
    }

    @Test
    void upload_createsTierFolderWhenMissing() throws IOException {
        when(list.execute()).thenReturn(new FileList());
        when(folderCreate.execute()).thenReturn(new File().setId("new-tier"));
        when(fileCreate.execute()).thenReturn(new File().setId("file-1"));

        store().upload(dump, "b.dump", BackupTier.DAILY);

        ArgumentCaptor<File> folder = ArgumentCaptor.forClass(File.class);
        verify(files).create(folder.capture());
        assertThat(folder.getValue().getName()).isEqualTo("daily");
        assertThat(folder.getValue().getMimeType()).isEqualTo("application/vnd.google-apps.folder");
        assertThat(folder.getValue().getParents()).containsExactly(ROOT);
        assertThat(folder.getValue().getAppProperties()).containsEntry("backupApp", "funnyapp-db");
        ArgumentCaptor<File> meta = ArgumentCaptor.forClass(File.class);
        verify(files).create(meta.capture(), any(AbstractInputStreamContent.class));
        assertThat(meta.getValue().getParents()).containsExactly("new-tier");
    }

    @Test
    void upload_severalTierFoldersPicksFirstOldest() throws IOException {
        when(list.execute()).thenReturn(folders("oldest", "newer"));
        when(fileCreate.execute()).thenReturn(new File().setId("f"));

        store().upload(dump, "b.dump", BackupTier.DAILY);

        ArgumentCaptor<File> meta = ArgumentCaptor.forClass(File.class);
        verify(files).create(meta.capture(), any(AbstractInputStreamContent.class));
        assertThat(meta.getValue().getParents()).containsExactly("oldest");
    }

    @Test
    void upload_cachesTierFolderAcrossUploads() throws IOException {
        when(list.execute()).thenReturn(folders("tier-1"));
        when(fileCreate.execute()).thenReturn(new File().setId("f"));
        DriveBackupStore store = store();

        store.upload(dump, "a.dump", BackupTier.DAILY);
        store.upload(dump, "b.dump", BackupTier.DAILY);

        verify(list, times(1)).execute();
        verify(fileCreate, times(2)).execute();
    }

    @Test
    void upload_notFoundOnCachedFolderReResolvesAndRetriesOnce() throws IOException {
        when(list.execute()).thenReturn(folders("stale"), folders("fresh"));
        when(fileCreate.execute()).thenThrow(http(404)).thenReturn(new File().setId("f"));

        String id = store().upload(dump, "a.dump", BackupTier.DAILY);

        assertThat(id).isEqualTo("f");
        verify(list, times(2)).execute();
        ArgumentCaptor<File> meta = ArgumentCaptor.forClass(File.class);
        verify(files, times(2)).create(meta.capture(), any(AbstractInputStreamContent.class));
        assertThat(meta.getAllValues().get(0).getParents()).containsExactly("stale");
        assertThat(meta.getAllValues().get(1).getParents()).containsExactly("fresh");
    }

    @Test
    void upload_secondNotFoundFailsWithUploadFailed() throws IOException {
        when(list.execute()).thenReturn(folders("t"));
        when(fileCreate.execute()).thenThrow(http(404));

        assertThatThrownBy(() -> store().upload(dump, "a.dump", BackupTier.DAILY))
                .isInstanceOfSatisfying(BackupException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(BackupErrorCode.DRIVE_UPLOAD_FAILED);
                    assertThat(e.getStep()).isEqualTo(BackupStep.UPLOAD);
                });
        verify(fileCreate, times(2)).execute();
    }

    @Test
    void upload_notFoundResolvingParentFolderIsFolderNotFound() throws IOException {
        when(list.execute()).thenThrow(http(404));

        assertThatThrownBy(() -> store().upload(dump, "a.dump", BackupTier.DAILY))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BackupErrorCode.DRIVE_FOLDER_NOT_FOUND));
    }

    @Test
    void upload_nullIdIsUploadFailed() throws IOException {
        when(list.execute()).thenReturn(folders("t"));
        when(fileCreate.execute()).thenReturn(new File());

        assertThatThrownBy(() -> store().upload(dump, "a.dump", BackupTier.DAILY))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BackupErrorCode.DRIVE_UPLOAD_FAILED));
    }

    @Test
    void upload_unauthorizedIsAuthFailedWithoutLeakingDetails() throws IOException {
        when(list.execute()).thenReturn(folders("t"));
        when(fileCreate.execute()).thenThrow(http(401));

        assertThatThrownBy(() -> store().upload(dump, "a.dump", BackupTier.DAILY))
                .isInstanceOfSatisfying(BackupException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(BackupErrorCode.DRIVE_AUTH_FAILED);
                    assertThat(e.getSafeReason()).doesNotContain("401");
                });
    }

    @Test
    void upload_invalidGrantMessageIsAuthFailed() throws IOException {
        when(list.execute()).thenThrow(new IOException("Error getting access token: invalid_grant"));

        assertThatThrownBy(() -> store().upload(dump, "a.dump", BackupTier.DAILY))
                .isInstanceOfSatisfying(BackupException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(BackupErrorCode.DRIVE_AUTH_FAILED);
                    assertThat(e.getMessage()).doesNotContain("invalid_grant");
                });
    }

    @Test
    void upload_otherErrorIsUploadFailed() throws IOException {
        when(list.execute()).thenReturn(folders("t"));
        when(fileCreate.execute()).thenThrow(http(500));

        assertThatThrownBy(() -> store().upload(dump, "a.dump", BackupTier.DAILY))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BackupErrorCode.DRIVE_UPLOAD_FAILED));
    }

    @Test
    void invalidFolderIdIsNotConfigured() {
        props.getBackup().setFolderId("bad' or '1'='1");

        assertThatThrownBy(() -> store().upload(dump, "a.dump", BackupTier.DAILY))
                .isInstanceOfSatisfying(BackupException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(BackupErrorCode.NOT_CONFIGURED);
                    assertThat(e.getStep()).isEqualTo(BackupStep.CONFIG);
                });
    }

    @Test
    void blankFolderIdIsNotConfigured() {
        props.getBackup().setFolderId("");

        assertThatThrownBy(() -> store().list(BackupTier.WEEKLY))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.getCode()).isNotNull());
    }

    @Test
    void list_usesExactQueryAndFollowsPages() throws IOException {
        FileList folder = folders("tier-w");
        FileList page1 = new FileList().setNextPageToken("tok").setFiles(List.of(
                new File().setId("a").setName("a.dump").setCreatedTime(new DateTime(2000L))));
        FileList page2 = new FileList().setFiles(List.of(new File().setId("b").setName("b.dump")));
        when(list.execute()).thenReturn(folder, page1, page2);

        List<BackupFile> result = store().list(BackupTier.WEEKLY);

        assertThat(result).containsExactly(
                new BackupFile("a", "a.dump", Instant.ofEpochMilli(2000L)),
                new BackupFile("b", "b.dump", null));
        verify(list, times(2)).setQ("'tier-w' in parents and trashed = false"
                + " and appProperties has { key='backupApp' and value='funnyapp-db' }"
                + " and appProperties has { key='backupTier' and value='weekly' }");
        verify(list, times(2)).setOrderBy("createdTime desc");
        verify(list, times(2)).setFields("nextPageToken, files(id, name, createdTime)");
        verify(list).setPageToken("tok");
    }

    @Test
    void list_emptyPageReturnsEmptyList() throws IOException {
        when(list.execute()).thenReturn(folders("t"), new FileList());

        assertThat(store().list(BackupTier.DAILY)).isEmpty();
    }

    @Test
    void list_ioErrorIsPruneFailed() throws IOException {
        when(list.execute()).thenReturn(folders("t")).thenThrow(http(500));

        assertThatThrownBy(() -> store().list(BackupTier.DAILY))
                .isInstanceOfSatisfying(BackupException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(BackupErrorCode.PRUNE_FAILED);
                    assertThat(e.getStep()).isEqualTo(BackupStep.PRUNE);
                });
    }

    @Test
    void list_resolveFailureIsPruneFailed() throws IOException {
        when(list.execute()).thenThrow(http(404));

        assertThatThrownBy(() -> store().list(BackupTier.DAILY))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BackupErrorCode.PRUNE_FAILED));
    }

    @Test
    void trash_setsTrashedTrue() throws IOException {
        Drive.Files.Update update = mock(Drive.Files.Update.class);
        when(files.update(eq("file-9"), any(File.class))).thenReturn(update);

        store().trash("file-9");

        ArgumentCaptor<File> body = ArgumentCaptor.forClass(File.class);
        verify(files).update(eq("file-9"), body.capture());
        assertThat(body.getValue().getTrashed()).isTrue();
        verify(update).execute();
    }

    @Test
    void trash_errorIsPruneFailed() throws IOException {
        Drive.Files.Update update = mock(Drive.Files.Update.class);
        when(files.update(eq("x"), any(File.class))).thenReturn(update);
        when(update.execute()).thenThrow(http(403));

        assertThatThrownBy(() -> store().trash("x"))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.getCode()).isEqualTo(BackupErrorCode.PRUNE_FAILED));
    }
}
