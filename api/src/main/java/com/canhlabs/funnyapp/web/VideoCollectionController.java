package com.canhlabs.funnyapp.web;

import com.canhlabs.funnyapp.aop.AuditLog;
import com.canhlabs.funnyapp.aop.RateLimited;
import com.canhlabs.funnyapp.dto.collection.CollectionDto;
import com.canhlabs.funnyapp.dto.collection.CollectionRequest;
import com.canhlabs.funnyapp.dto.collection.MoveVideoRequest;
import com.canhlabs.funnyapp.dto.video.VideoDto;
import com.canhlabs.funnyapp.dto.webapi.ResultListInfo;
import com.canhlabs.funnyapp.dto.webapi.ResultObjectInfo;
import com.canhlabs.funnyapp.enums.ResultStatus;
import com.canhlabs.funnyapp.service.VideoCollectionService;
import com.canhlabs.funnyapp.utils.AppConstant;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(AppConstant.API.BASE_URL + "/collections")
public class VideoCollectionController {

    private final VideoCollectionService collectionService;

    public VideoCollectionController(VideoCollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @Operation(summary = "My collections", description = "Creates the default \"Xem sau\" collection on first call. Admins also get every public collection.")
    @GetMapping
    @RateLimited(permit = 60)
    public ResponseEntity<ResultListInfo<CollectionDto>> getMine() {
        return list(collectionService.getMine());
    }

    @Operation(summary = "Public collections", description = "Public; curated by admins.")
    @GetMapping("/public")
    @RateLimited(permit = 60)
    public ResponseEntity<ResultListInfo<CollectionDto>> getPublic() {
        return list(collectionService.getPublic());
    }

    @Operation(summary = "Collection membership of a video", description = "Ids of the caller's manageable collections containing the video.")
    @GetMapping("/membership")
    @RateLimited(permit = 60)
    public ResponseEntity<ResultListInfo<UUID>> getMembership(@RequestParam Long videoId) {
        return list(collectionService.getMembership(videoId));
    }

    @Operation(summary = "Create a collection", description = "PUBLIC visibility requires ADMIN or SUPER_ADMIN.")
    @PostMapping
    @RateLimited(permit = 30)
    @AuditLog("createCollection")
    public ResponseEntity<ResultObjectInfo<CollectionDto>> create(@Valid @RequestBody CollectionRequest request) {
        return object(collectionService.create(request), HttpStatus.CREATED);
    }

    @Operation(summary = "Rename / change visibility", description = "Null fields stay unchanged.")
    @PatchMapping("/{id}")
    @RateLimited(permit = 30)
    @AuditLog("updateCollection")
    public ResponseEntity<ResultObjectInfo<CollectionDto>> update(@PathVariable UUID id,
                                                                  @Valid @RequestBody CollectionRequest request) {
        return object(collectionService.update(id, request), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @RateLimited(permit = 30)
    @AuditLog("deleteCollection")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        collectionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Videos in a collection", description = "Public collections: anyone. Private: owner only.")
    @GetMapping("/{id}/videos")
    @RateLimited(permit = 60)
    public ResponseEntity<ResultObjectInfo<Page<VideoDto>>> getVideos(@PathVariable UUID id,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "24") int size) {
        return object(collectionService.getVideos(id, page, size), HttpStatus.OK);
    }

    @Operation(summary = "Video ids in a collection", description = "Managers only; used to mark videos already added.")
    @GetMapping("/{id}/video-ids")
    @RateLimited(permit = 60)
    public ResponseEntity<ResultListInfo<Long>> getVideoIds(@PathVariable UUID id) {
        return list(collectionService.getVideoIds(id));
    }

    @PutMapping("/{id}/videos/{videoId}")
    @RateLimited(permit = 30)
    @AuditLog("addVideoToCollection")
    public ResponseEntity<Void> addVideo(@PathVariable UUID id, @PathVariable Long videoId) {
        collectionService.addVideo(id, videoId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/videos/{videoId}")
    @RateLimited(permit = 30)
    @AuditLog("removeVideoFromCollection")
    public ResponseEntity<Void> removeVideo(@PathVariable UUID id, @PathVariable Long videoId) {
        collectionService.removeVideo(id, videoId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Move a video within a collection", description = "Places it right after afterVideoId; null = top.")
    @PatchMapping("/{id}/videos/{videoId}")
    @RateLimited(permit = 60)
    @AuditLog("moveVideoInCollection")
    public ResponseEntity<Void> moveVideo(@PathVariable UUID id, @PathVariable Long videoId,
                                          @RequestBody MoveVideoRequest request) {
        collectionService.moveVideo(id, videoId, request.getAfterVideoId());
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<ResultListInfo<T>> list(List<T> data) {
        return ResponseEntity.ok(ResultListInfo.<T>builder().status(ResultStatus.SUCCESS).data(data).build());
    }

    private static <T> ResponseEntity<ResultObjectInfo<T>> object(T data, HttpStatus status) {
        return new ResponseEntity<>(ResultObjectInfo.<T>builder().status(ResultStatus.SUCCESS).data(data).build(), status);
    }
}
