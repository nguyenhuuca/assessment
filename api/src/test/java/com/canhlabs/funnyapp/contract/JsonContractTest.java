package com.canhlabs.funnyapp.contract;

import com.canhlabs.funnyapp.dto.admin.AdminVideoDto;
import com.canhlabs.funnyapp.dto.admin.CreateVideoImportResultDto;
import com.canhlabs.funnyapp.dto.admin.DriveSyncItemDto;
import com.canhlabs.funnyapp.dto.admin.DriveSyncRunDto;
import com.canhlabs.funnyapp.dto.admin.ReportDto;
import com.canhlabs.funnyapp.dto.admin.ScheduledJobDto;
import com.canhlabs.funnyapp.dto.admin.VideoImportJobDto;
import com.canhlabs.funnyapp.dto.collection.CollectionDto;
import com.canhlabs.funnyapp.dto.comment.CommentNode;
import com.canhlabs.funnyapp.dto.comment.CreateCommentResponse;
import com.canhlabs.funnyapp.dto.notification.NotificationDto;
import com.canhlabs.funnyapp.dto.notification.UnreadCountDto;
import com.canhlabs.funnyapp.dto.reaction.ReactionSummaryDto;
import com.canhlabs.funnyapp.dto.suggestion.SuggestionDto;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.user.UserInfoDto;
import com.canhlabs.funnyapp.dto.usersettings.AccountStatusDto;
import com.canhlabs.funnyapp.dto.usersettings.UserSettingsDto;
import com.canhlabs.funnyapp.dto.video.VideoDto;
import com.canhlabs.funnyapp.dto.watchhistory.WatchHistoryItemDto;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.CollectionVisibility;
import com.canhlabs.funnyapp.enums.DriveSyncItemStatus;
import com.canhlabs.funnyapp.enums.HlsStatus;
import com.canhlabs.funnyapp.enums.ImportPlatform;
import com.canhlabs.funnyapp.enums.ImportStatus;
import com.canhlabs.funnyapp.enums.NotificationType;
import com.canhlabs.funnyapp.enums.ReactionType;
import com.canhlabs.funnyapp.enums.ReportAutoLabel;
import com.canhlabs.funnyapp.enums.ReportStatus;
import com.canhlabs.funnyapp.enums.ReportType;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.enums.SuggestionRejectReason;
import com.canhlabs.funnyapp.enums.SuggestionStatus;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.dto.webapi.ResultErrorInfo;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.exception.Error;
import com.canhlabs.funnyapp.exception.UnauthorizedException;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the JSON wire format of every controller DTO inside its real wrapper, recorded from Spring Boot 3.5
 * (spec spring-boot-4-upgrade Rule 2/3). A Jackson 3 migration must keep these fixtures green.
 */
class JsonContractTest {

    private static final JsonMapper MAPPER = JsonContractSupport.bootMapper();
    private static final Instant T1 = Instant.parse("2026-01-02T03:04:05Z");
    private static final Instant T2 = Instant.parse("2026-02-03T04:05:06Z");
    private static final UUID ID1 = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ID2 = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static <T> ResultObjectInfo<T> obj(T data) {
        return ResultObjectInfo.<T>builder().status(ResultStatus.SUCCESS).data(data).build();
    }

    @SafeVarargs
    private static <T> ResultListInfo<T> list(T... data) {
        return ResultListInfo.<T>builder().status(ResultStatus.SUCCESS).data(List.of(data)).build();
    }

    static Stream<Arguments> wrappedDtos() {
        VideoDto video = VideoDto.builder().id(1L).userShared("user@example.test").title("Funny cat")
                .desc("A cat").embedLink("https://example.test/embed/1").urlLink("https://example.test/v/1")
                .fileId("file-1").viewCount(12L).hlsUrl("https://example.test/hls/1/index.m3u8").build();
        VideoDto videoNulls = VideoDto.builder().id(2L).title("No extras").build();
        WatchHistoryItemDto history = WatchHistoryItemDto.builder().videoId(1L).title("Funny cat").fileId("file-1")
                .viewCount(12L).watchedAt(T1).positionSec(30).durationSec(120).completed(true).build();
        AdminVideoDto adminVideo = AdminVideoDto.builder().id(1L).title("Funny cat").thumbnailPath("/thumbs/1.jpg")
                .creatorEmail("user@example.test").status(VideoStatus.PUBLISHED).hidden(true).viewCount(12L)
                .priority(3).createdAt(T1).hlsStatus(HlsStatus.READY).hlsError(null).build();
        VideoImportJobDto job = VideoImportJobDto.builder().id(9L).sourceUrl("https://youtu.be/abc")
                .platform(ImportPlatform.YOUTUBE).title("Imported").status(ImportStatus.DOWNLOADING)
                .phase("download").progressPct(40).downloadedBytes(400L).totalBytes(1000L).scheduledAt(T1)
                .startedAt(T2).finishedAt(null).driveFileId("drive-1").ingested(false).errorCode(null)
                .errorMessage(null).attempts(2).createdAt(T1).build();
        ReportDto report = ReportDto.builder().id(ID1).type(ReportType.BUG).title("Broken").status(ReportStatus.CREATED)
                .issueNumber(40).issueUrl("https://example.test/issues/40").labels(List.of("bug", "autopilot"))
                .autopilot(true).autoLabel(ReportAutoLabel.AUTO_MERGE).capReached(false).error(null)
                .reporter("user@example.test").githubState("closed").githubStateReason("completed")
                .createdAt(T1).build();
        DriveSyncRunDto sync = DriveSyncRunDto.builder().runId("run-1").startedAt(T1).finishedAt(null).active(true)
                .items(List.of(DriveSyncItemDto.builder().fileId("f1").name("a.mp4").status(DriveSyncItemStatus.FAILED)
                        .downloadedBytes(10L).totalBytes(20L).errorCode("DISK_FULL").build())).build();
        ScheduledJobDto scheduled = ScheduledJobDto.builder().name("drive-sync").description("Sync").cron("0 0 * * * *")
                .zone("UTC").enabled(true).nextRunAt(T2).lastRunAt(T1).lastResult("OK").running(false).build();
        SuggestionDto suggestion = SuggestionDto.builder().id(5L).sourceUrl("https://youtu.be/abc")
                .platform(ImportPlatform.FACEBOOK).title("Suggested").note("please").status(SuggestionStatus.REJECTED)
                .live(false).liveVideoId(null).liveFileId(null).rejectReason(SuggestionRejectReason.LOW_QUALITY)
                .rejectNote("blurry").createdAt(T1).reviewedAt(T2).suggesterName("user").importJobId(9L)
                .rereviewCount(1).maxRereviews(2).canRequestRereview(true).canReopen(false).build();
        CommentNode comment = CommentNode.builder().id(ID1).videoId("22").userId("user@example.test").guestName(null)
                .content("hello").createdAt(T1).updatedAt(T2).parentId(null).removed(false).deleted(false)
                .replies(List.of(CommentNode.builder().id(ID2).videoId("22").userId(null).guestName("guest")
                        .content("hi").createdAt(T2).updatedAt(T2).parentId(ID1.toString()).replies(List.of())
                        .removed(true).deleted(true).build())).build();
        UserSettingsDto settings = UserSettingsDto.builder().email("user@example.test").passwordEnabled(true)
                .passwordLoginAvailable(true).mfaEnabled(false).mfaAvailable(true).notifyNewContent(true)
                .notifyEmail(false).defaultQuality("720p").incognitoEnabled(false).profilePrivate(true).locale("vi")
                .accountStatus(AccountStatusDto.builder().plan("FREE").renewsAt(null).build()).updatedAt(T1).build();
        NotificationDto notification = NotificationDto.builder().id(ID1).type(NotificationType.COMMENT_REPLY)
                .videoId("22").commentId(ID2).actorDisplay("u***").actorCount(2).snippet("haha").reason(null)
                .read(false).updatedAt(T1).build();
        CollectionDto collection = CollectionDto.builder().id(ID1).name("Favourites")
                .visibility(CollectionVisibility.PUBLIC).isDefault(true).ownerName("user").itemCount(3L)
                .unavailableCount(null).coverVideoId(1L).coverFileId("file-1").canManage(true).updatedAt(T1).build();
        ReactionSummaryDto reaction = ReactionSummaryDto.builder().videoId(1L).likeCount(5L).dislikeCount(1L)
                .myReaction(ReactionType.LIKE).build();
        UserDetailDto detail = UserDetailDto.builder().id(42L).email("user@example.test").mfaEnabled(true)
                .role("ADMIN").permissions(5).passwordEnabled(true).mfaAvailable(true).passwordLoginAvailable(true)
                .credentialsVersion(3).build();
        UserInfoDto loginFull = UserInfoDto.builder().jwt("fake.jwt.token").action("LOGIN")
                .sessionToken("fake-session").user(detail).build();
        UserInfoDto loginNulls = UserInfoDto.builder().jwt(null).action(null).sessionToken(null).user(null).build();
        CreateVideoImportResultDto imports = CreateVideoImportResultDto.builder().results(List.of(
                CreateVideoImportResultDto.LineResult.builder().line(1).jobId(9L).errorCode(null).build(),
                CreateVideoImportResultDto.LineResult.builder().line(2).jobId(null).errorCode("INVALID_URL").build()))
                .build();

        return Stream.of(
                Arguments.of("video", obj(video)),
                Arguments.of("video-nulls", obj(videoNulls)),
                Arguments.of("video-list", list(video, videoNulls)),
                Arguments.of("watch-history-page", obj(new PageImpl<>(List.of(history), PageRequest.of(0, 20), 1))),
                Arguments.of("admin-video", obj(adminVideo)),
                Arguments.of("video-import-job", obj(job)),
                Arguments.of("report", obj(report)),
                Arguments.of("drive-sync-run", obj(sync)),
                Arguments.of("scheduled-job-list", list(scheduled)),
                Arguments.of("suggestion", obj(suggestion)),
                Arguments.of("comment-node", obj(comment)),
                Arguments.of("create-comment-response", obj(CreateCommentResponse.builder().id(ID1).build())),
                Arguments.of("user-settings", obj(settings)),
                Arguments.of("notification", obj(notification)),
                Arguments.of("unread-count", obj(UnreadCountDto.builder().count(7L).build())),
                Arguments.of("collection", obj(collection)),
                Arguments.of("reaction-summary", obj(reaction)),
                Arguments.of("user-info", obj(loginFull)),
                Arguments.of("user-info-nulls", obj(loginNulls)),
                Arguments.of("create-video-import-result", obj(imports)));
    }

    @ParameterizedTest(name = "dto-{0}.json")
    @MethodSource("wrappedDtos")
    void wrappedDto_serializesToRecordedFixture(String name, Object wrapped) throws Exception {
        JsonContractSupport.assertMatchesFixture("dto-" + name + ".json", MAPPER.writeValueAsString(wrapped));
    }

    private static final String LEGACY_TIMESTAMP = "\\d{2}-\\d{2}-\\d{4} \\d{2}:\\d{2}:\\d{2}";
    private static final String ISO_LOCAL_DATE_TIME = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?";

    @Test
    void error_withCode_serializesToRecordedFixture() throws Exception {
        Error error = new Error(HttpStatus.NOT_FOUND, 404, "Video not found");
        error.setCode("video.notFound");
        error.setTimestamp(LocalDateTime.of(2026, 1, 2, 15, 4, 5));

        JsonContractSupport.assertMatchesFixture("error-code.json", MAPPER.writeValueAsString(error));
    }

    @Test
    void error_withFieldSubErrors_serializesToRecordedFixture() throws Exception {
        Error error = new Error(HttpStatus.BAD_REQUEST, 400, "Validation failed");
        error.setTimestamp(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        error.addValidationError(new FieldError("loginRequest", "email", "not-an-email", false, null, null,
                "must be a well-formed email address"));

        JsonContractSupport.assertMatchesFixture("error-field-sub-errors.json", MAPPER.writeValueAsString(error));
    }

    @Test
    void resultErrorInfo_fromCustomException_serializesToRecordedFixture() throws Exception {
        ResultErrorInfo info = CustomException.of(HttpStatus.CONFLICT, 409, "comment.duplicate").buildErrorMessage();

        String json = JsonContractSupport.stripTimestamp(MAPPER.writeValueAsString(info), "", "timestamp",
                ISO_LOCAL_DATE_TIME);

        JsonContractSupport.assertMatchesFixture("error-result-error-info.json", json);
    }

    @Test
    void unauthorizedException_toJson_serializesToRecordedFixture() throws Exception {
        String json = JsonContractSupport.stripTimestamp(new UnauthorizedException("TOKEN_INVALID", 601).toJson(),
                "/error", "timestamp", LEGACY_TIMESTAMP);

        JsonContractSupport.assertMatchesFixture("error-unauthorized.json", json);
    }

    @Test
    void userDetail_neverExposesCredentialsVersion() throws Exception {
        UserDetailDto detail = UserDetailDto.builder().id(42L).email("user@example.test").credentialsVersion(3).build();

        assertThat(MAPPER.writeValueAsString(detail)).doesNotContain("credentialsVersion");
    }

    @Test
    void collection_exposesIsDefaultKey() throws Exception {
        String json = MAPPER.writeValueAsString(CollectionDto.builder().isDefault(true).build());

        assertThat(json).contains("\"isDefault\":true").doesNotContain("\"default\"");
    }
}
