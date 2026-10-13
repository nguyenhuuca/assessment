package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.collection.CollectionDto;
import com.canhlabs.funnyapp.dto.collection.CollectionRequest;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.video.VideoDto;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.VideoCollection;
import com.canhlabs.funnyapp.enums.CollectionVisibility;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.VideoCollectionItemRepository;
import com.canhlabs.funnyapp.repo.VideoCollectionItemRepository.Cover;
import com.canhlabs.funnyapp.repo.VideoCollectionItemRepository.ItemCounts;
import com.canhlabs.funnyapp.repo.VideoCollectionRepository;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import com.canhlabs.funnyapp.service.VideoCollectionService;
import com.canhlabs.funnyapp.utils.AppUtils;
import com.canhlabs.funnyapp.utils.NotificationText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoCollectionServiceImpl implements VideoCollectionService {

    public static final String DEFAULT_NAME = "Xem sau";
    static final int MAX_COLLECTIONS_PER_USER = 50;
    static final int MAX_ITEMS_PER_COLLECTION = 500;
    static final int MAX_PAGE_SIZE = 50;

    private final VideoCollectionRepository collectionRepository;
    private final VideoCollectionItemRepository itemRepository;
    private final VideoSourceRepository videoSourceRepository;
    private final UserRepo userRepo;

    @Override
    @Transactional
    public List<CollectionDto> getMine() {
        UserDetailDto user = requireUser();
        ensureDefault(user.getId());
        boolean admin = isAdmin(user.getId());
        return toDtos(collectionRepository.findManageable(user.getId(), admin), c -> true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollectionDto> getPublic() {
        UserDetailDto user = AppUtils.getCurrentUser();
        boolean admin = user != null && user.getId() != null && isAdmin(user.getId());
        return toDtos(collectionRepository.findByVisibilityOrderByUpdatedAtDesc(CollectionVisibility.PUBLIC), c -> admin)
                .stream()
                .filter(dto -> admin || dto.getItemCount() > 0)
                .toList();
    }

    @Override
    @Transactional
    public CollectionDto create(CollectionRequest request) {
        UserDetailDto user = requireUser();
        Long userId = user.getId();
        ensureDefault(userId);
        String name = normalizeName(request.getName());
        CollectionVisibility visibility = request.getVisibility() == null ? CollectionVisibility.PRIVATE : request.getVisibility();
        if (visibility == CollectionVisibility.PUBLIC && !isAdmin(userId)) {
            throw error(HttpStatus.FORBIDDEN, "Only admins can create public collections");
        }
        if (collectionRepository.countByUserId(userId) >= MAX_COLLECTIONS_PER_USER) {
            throw error(HttpStatus.CONFLICT, "Collection limit reached (" + MAX_COLLECTIONS_PER_USER + ")");
        }
        requireUniqueName(userId, name, visibility, null);

        VideoCollection collection = new VideoCollection();
        collection.setUserId(userId);
        collection.setName(name);
        collection.setVisibility(visibility);
        VideoCollection saved = saveUnique(collection);
        log.info("Collection {} created by user {} ({})", saved.getId(), userId, visibility);
        return toDtos(List.of(saved), c -> true).getFirst();
    }

    @Override
    @Transactional
    public CollectionDto update(UUID id, CollectionRequest request) {
        UserDetailDto user = requireUser();
        boolean admin = isAdmin(user.getId());
        VideoCollection collection = requireManageable(id, user.getId(), admin);
        if (collection.isDefault()) {
            throw error(HttpStatus.BAD_REQUEST, "The default collection cannot be changed");
        }
        CollectionVisibility visibility = request.getVisibility() == null ? collection.getVisibility() : request.getVisibility();
        if (visibility == CollectionVisibility.PUBLIC && !admin) {
            throw error(HttpStatus.FORBIDDEN, "Only admins can make a collection public");
        }
        String name = request.getName() == null ? collection.getName() : normalizeName(request.getName());
        requireUniqueName(collection.getUserId(), name, visibility, collection.getId());

        collection.setName(name);
        collection.setVisibility(visibility);
        VideoCollection saved = saveUnique(collection);
        return toDtos(List.of(saved), c -> true).getFirst();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        UserDetailDto user = requireUser();
        VideoCollection collection = requireManageable(id, user.getId(), isAdmin(user.getId()));
        if (collection.isDefault()) {
            throw error(HttpStatus.BAD_REQUEST, "The default collection cannot be deleted");
        }
        collectionRepository.delete(collection);
        log.info("Collection {} deleted by user {}", id, user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VideoDto> getVideos(UUID id, int page, int size) {
        UserDetailDto user = AppUtils.getCurrentUser();
        Long userId = user == null ? null : user.getId();
        VideoCollection collection = collectionRepository.findById(id)
                .filter(c -> c.getVisibility() == CollectionVisibility.PUBLIC || Objects.equals(c.getUserId(), userId))
                .orElseThrow(this::notFound);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return itemRepository.findVisibleVideos(collection.getId(), pageable).map(Converter::toVideoDto);
    }

    @Override
    @Transactional
    public void addVideo(UUID id, Long videoId) {
        UserDetailDto user = requireUser();
        VideoCollection collection = requireManageable(id, user.getId(), isAdmin(user.getId()));
        if (videoId == null || !videoSourceRepository.existsByIdAndIsHideAndStatus(videoId, false, VideoStatus.PUBLISHED)) {
            throw error(HttpStatus.NOT_FOUND, "Video not found");
        }
        if (itemRepository.countItems(collection.getId()) >= MAX_ITEMS_PER_COLLECTION) {
            throw error(HttpStatus.CONFLICT, "Collection is full (" + MAX_ITEMS_PER_COLLECTION + " videos)");
        }
        if (itemRepository.addItem(collection.getId(), videoId) > 0) {
            touch(collection);
        }
    }

    @Override
    @Transactional
    public void removeVideo(UUID id, Long videoId) {
        UserDetailDto user = requireUser();
        VideoCollection collection = requireManageable(id, user.getId(), isAdmin(user.getId()));
        if (itemRepository.removeItem(collection.getId(), videoId) > 0) {
            touch(collection);
        }
    }

    @Override
    @Transactional
    public List<UUID> getMembership(Long videoId) {
        UserDetailDto user = requireUser();
        ensureDefault(user.getId());
        List<UUID> ids = collectionRepository.findManageable(user.getId(), isAdmin(user.getId())).stream()
                .map(VideoCollection::getId)
                .toList();
        if (videoId == null || ids.isEmpty()) {
            return List.of();
        }
        return itemRepository.findCollectionIdsContaining(videoId, ids).stream().map(UUID::fromString).toList();
    }

    // ---- helpers ----

    private void ensureDefault(Long userId) {
        if (collectionRepository.findByUserIdAndIsDefaultTrue(userId).isEmpty()) {
            collectionRepository.insertDefaultIfMissing(userId, DEFAULT_NAME);
        }
    }

    /** Read from the DB, not the JWT: a token issued before a demotion still carries the old role. */
    private boolean isAdmin(Long userId) {
        return userRepo.findById(userId)
                .map(User::getRole)
                .map(role -> role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN)
                .orElse(false);
    }

    /**
     * PUBLIC: any admin manages it. PRIVATE: owner only. Anything else answers 404 so ids cannot be probed.
     */
    private VideoCollection requireManageable(UUID id, Long userId, boolean admin) {
        return collectionRepository.findById(id)
                .filter(c -> c.getVisibility() == CollectionVisibility.PUBLIC ? admin : Objects.equals(c.getUserId(), userId))
                .orElseThrow(this::notFound);
    }

    private void requireUniqueName(Long ownerId, String name, CollectionVisibility visibility, UUID excludeId) {
        boolean ownerClash = excludeId == null
                ? collectionRepository.existsByUserIdAndNameIgnoreCase(ownerId, name)
                : collectionRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(ownerId, name, excludeId);
        boolean publicClash = visibility == CollectionVisibility.PUBLIC && (excludeId == null
                ? collectionRepository.existsByVisibilityAndNameIgnoreCase(CollectionVisibility.PUBLIC, name)
                : collectionRepository.existsByVisibilityAndNameIgnoreCaseAndIdNot(CollectionVisibility.PUBLIC, name, excludeId));
        if (ownerClash || publicClash) {
            throw error(HttpStatus.CONFLICT, "A collection named \"" + name + "\" already exists");
        }
    }

    /** The unique indexes are the source of truth; the exists-checks above only give a friendlier message. */
    private VideoCollection saveUnique(VideoCollection collection) {
        try {
            return collectionRepository.saveAndFlush(collection);
        } catch (DataIntegrityViolationException e) {
            throw error(HttpStatus.CONFLICT, "A collection named \"" + collection.getName() + "\" already exists");
        }
    }

    private void touch(VideoCollection collection) {
        collection.setUpdatedAt(Instant.now());
        collectionRepository.save(collection);
    }

    static String normalizeName(String raw) {
        String name = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > CollectionRequest.MAX_NAME) {
            throw error(HttpStatus.BAD_REQUEST, "Collection name must be 1-" + CollectionRequest.MAX_NAME + " characters");
        }
        return name;
    }

    private List<CollectionDto> toDtos(List<VideoCollection> collections, Predicate<VideoCollection> canManage) {
        if (collections.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = collections.stream().map(VideoCollection::getId).toList();
        Map<String, ItemCounts> counts = itemRepository.countByCollection(ids).stream()
                .collect(Collectors.toMap(ItemCounts::getCollectionId, Function.identity()));
        Map<String, Cover> covers = itemRepository.findCovers(ids).stream()
                .collect(Collectors.toMap(Cover::getCollectionId, Function.identity()));
        Map<Long, String> owners = ownerNames(collections.stream().map(VideoCollection::getUserId).collect(Collectors.toSet()));

        return collections.stream().map(c -> {
            String key = c.getId().toString();
            ItemCounts count = counts.get(key);
            Cover cover = covers.get(key);
            boolean manage = canManage.test(c);
            return CollectionDto.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .visibility(c.getVisibility())
                    .isDefault(c.isDefault())
                    .ownerName(owners.get(c.getUserId()))
                    .itemCount(count == null ? 0 : count.getVisibleCount())
                    .unavailableCount(manage ? (count == null ? 0L : count.getUnavailableCount()) : null)
                    .coverVideoId(cover == null ? null : cover.getVideoId())
                    .coverFileId(cover == null ? null : cover.getFileId())
                    .canManage(manage)
                    .updatedAt(c.getUpdatedAt())
                    .build();
        }).toList();
    }

    private Map<Long, String> ownerNames(Collection<Long> userIds) {
        return userRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> Objects.toString(NotificationText.actorDisplay(u.getUserName()), "")));
    }

    private UserDetailDto requireUser() {
        UserDetailDto user = AppUtils.getCurrentUser();
        if (user == null || user.getId() == null) {
            throw error(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return user;
    }

    private CustomException notFound() {
        return error(HttpStatus.NOT_FOUND, "Collection not found");
    }

    private static CustomException error(HttpStatus status, String message) {
        return CustomException.builder().status(status).subCode(status.value()).message(message).build();
    }
}
