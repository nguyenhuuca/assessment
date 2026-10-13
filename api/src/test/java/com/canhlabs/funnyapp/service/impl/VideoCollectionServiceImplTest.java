package com.canhlabs.funnyapp.service.impl;

import com.canhlabs.funnyapp.dto.collection.CollectionDto;
import com.canhlabs.funnyapp.dto.collection.CollectionRequest;
import com.canhlabs.funnyapp.dto.user.UserDetailDto;
import com.canhlabs.funnyapp.dto.video.VideoDto;
import com.canhlabs.funnyapp.entity.User;
import com.canhlabs.funnyapp.entity.VideoCollection;
import com.canhlabs.funnyapp.entity.VideoSource;
import com.canhlabs.funnyapp.enums.CollectionVisibility;
import com.canhlabs.funnyapp.enums.UserRole;
import com.canhlabs.funnyapp.enums.VideoStatus;
import com.canhlabs.funnyapp.exception.CustomException;
import com.canhlabs.funnyapp.repo.UserRepo;
import com.canhlabs.funnyapp.repo.VideoCollectionItemRepository;
import com.canhlabs.funnyapp.repo.VideoCollectionRepository;
import com.canhlabs.funnyapp.repo.VideoSourceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoCollectionServiceImplTest {

    private static final long USER = 7L;
    private static final long OTHER = 8L;

    @Mock VideoCollectionRepository collectionRepository;
    @Mock VideoCollectionItemRepository itemRepository;
    @Mock VideoSourceRepository videoSourceRepository;
    @Mock UserRepo userRepo;
    @InjectMocks VideoCollectionServiceImpl service;

    @BeforeEach
    void setUp() {
        when(collectionRepository.findByUserIdAndIsDefaultTrue(anyLong())).thenReturn(Optional.of(new VideoCollection()));
        when(itemRepository.countByCollection(anyCollection())).thenReturn(List.of());
        when(itemRepository.findCovers(anyCollection())).thenReturn(List.of());
        when(collectionRepository.saveAndFlush(any())).thenAnswer(inv -> {
            VideoCollection c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            return c;
        });
        role(USER, UserRole.USER);
        role(OTHER, UserRole.USER);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void login(long id) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", null, List.of());
        auth.setDetails(UserDetailDto.builder().id(id).email("user" + id + "@x.com").build());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void role(long id, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setUserName("user" + id + "@x.com");
        u.setRole(role);
        when(userRepo.findById(id)).thenReturn(Optional.of(u));
        when(userRepo.findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any())).thenReturn(List.of(u));
    }

    private VideoCollection stored(long owner, CollectionVisibility visibility, boolean isDefault) {
        VideoCollection c = new VideoCollection();
        c.setId(UUID.randomUUID());
        c.setUserId(owner);
        c.setName("C-" + owner);
        c.setVisibility(visibility);
        c.setDefault(isDefault);
        when(collectionRepository.findById(c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getStatus())
                .isEqualTo(status);
    }

    private static CollectionRequest req(String name, CollectionVisibility visibility) {
        return CollectionRequest.builder().name(name).visibility(visibility).build();
    }

    // ---- reads ----

    @Test
    void getMine_requiresLogin() {
        assertStatus(service::getMine, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getMine_createsDefaultWhenMissing_andAdminsIncludePublic() {
        login(USER);
        role(USER, UserRole.ADMIN);
        when(collectionRepository.findByUserIdAndIsDefaultTrue(USER)).thenReturn(Optional.empty());
        VideoCollection def = stored(USER, CollectionVisibility.PRIVATE, true);
        when(collectionRepository.findManageable(USER, true)).thenReturn(List.of(def));

        List<CollectionDto> result = service.getMine();

        verify(collectionRepository).insertDefaultIfMissing(USER, VideoCollectionServiceImpl.DEFAULT_NAME);
        assertThat(result).singleElement().satisfies(d -> {
            assertThat(d.isDefault()).isTrue();
            assertThat(d.isCanManage()).isTrue();
            assertThat(d.getUnavailableCount()).isZero();
            assertThat(d.getOwnerName()).isEqualTo("user7");
        });
    }

    @Test
    void getMine_existingDefault_isNotRecreated() {
        login(USER);
        when(collectionRepository.findManageable(USER, false)).thenReturn(List.of());

        assertThat(service.getMine()).isEmpty();
        verify(collectionRepository, never()).insertDefaultIfMissing(anyLong(), anyString());
    }

    @Test
    void getPublic_guestSeesNonEmptyOnly_andNoUnavailableCount() {
        VideoCollection full = stored(OTHER, CollectionVisibility.PUBLIC, false);
        VideoCollection empty = stored(OTHER, CollectionVisibility.PUBLIC, false);
        when(collectionRepository.findByVisibilityOrderByUpdatedAtDesc(CollectionVisibility.PUBLIC))
                .thenReturn(List.of(full, empty));
        when(itemRepository.countByCollection(anyCollection())).thenReturn(List.of(counts(full.getId(), 3, 2)));
        when(itemRepository.findCovers(anyCollection())).thenReturn(List.of(cover(full.getId(), 5L, "f5")));

        List<CollectionDto> result = service.getPublic();

        assertThat(result).singleElement().satisfies(d -> {
            assertThat(d.getId()).isEqualTo(full.getId());
            assertThat(d.getItemCount()).isEqualTo(3);
            assertThat(d.getUnavailableCount()).isNull();
            assertThat(d.isCanManage()).isFalse();
            assertThat(d.getCoverFileId()).isEqualTo("f5");
            assertThat(d.getCoverVideoId()).isEqualTo(5L);
        });
    }

    @Test
    void getPublic_adminSeesEmptyAndUnavailableCount() {
        login(USER);
        role(USER, UserRole.SUPER_ADMIN);
        VideoCollection full = stored(OTHER, CollectionVisibility.PUBLIC, false);
        VideoCollection empty = stored(OTHER, CollectionVisibility.PUBLIC, false);
        when(collectionRepository.findByVisibilityOrderByUpdatedAtDesc(CollectionVisibility.PUBLIC))
                .thenReturn(List.of(full, empty));
        when(itemRepository.countByCollection(anyCollection())).thenReturn(List.of(counts(full.getId(), 3, 2)));

        List<CollectionDto> result = service.getPublic();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getUnavailableCount()).isEqualTo(2L);
        assertThat(result.getFirst().isCanManage()).isTrue();
    }

    @Test
    void getVideos_privateOfAnotherUser_is404_publicIsOpenToGuests() {
        VideoCollection priv = stored(OTHER, CollectionVisibility.PRIVATE, false);
        VideoCollection pub = stored(OTHER, CollectionVisibility.PUBLIC, false);
        VideoSource v = VideoSource.builder().id(5L).sourceId("f5").title("t").status(VideoStatus.PUBLISHED).build();
        when(itemRepository.findVisibleVideos(eq(pub.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(v), PageRequest.of(0, 50), 1));

        assertStatus(() -> service.getVideos(priv.getId(), 0, 10), HttpStatus.NOT_FOUND);
        Page<VideoDto> page = service.getVideos(pub.getId(), -3, 999);

        assertThat(page.getContent()).singleElement().extracting(VideoDto::getFileId).isEqualTo("f5");
        verify(itemRepository).findVisibleVideos(pub.getId(), PageRequest.of(0, VideoCollectionServiceImpl.MAX_PAGE_SIZE));
    }

    @Test
    void getVideos_ownerReadsPrivate() {
        login(USER);
        VideoCollection mine = stored(USER, CollectionVisibility.PRIVATE, false);
        when(itemRepository.findVisibleVideos(eq(mine.getId()), any(Pageable.class))).thenReturn(Page.empty());

        assertThat(service.getVideos(mine.getId(), 0, 10)).isEmpty();
    }

    // ---- create ----

    @Test
    void create_privateTrimsName() {
        login(USER);

        CollectionDto dto = service.create(req("  Hài   hước ", null));

        assertThat(dto.getName()).isEqualTo("Hài hước");
        assertThat(dto.getVisibility()).isEqualTo(CollectionVisibility.PRIVATE);
    }

    @Test
    void create_blankName_is400() {
        login(USER);
        assertStatus(() -> service.create(req("   ", null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(req(null, null)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_publicByNonAdmin_is403() {
        login(USER);
        assertStatus(() -> service.create(req("Hot", CollectionVisibility.PUBLIC)), HttpStatus.FORBIDDEN);
        verify(collectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_publicByAdmin_checksGlobalPublicName() {
        login(USER);
        role(USER, UserRole.ADMIN);
        when(collectionRepository.existsByVisibilityAndNameIgnoreCase(CollectionVisibility.PUBLIC, "Hot")).thenReturn(true);

        assertStatus(() -> service.create(req("Hot", CollectionVisibility.PUBLIC)), HttpStatus.CONFLICT);
    }

    @Test
    void create_duplicateOwnName_is409() {
        login(USER);
        when(collectionRepository.existsByUserIdAndNameIgnoreCase(USER, "xem sau")).thenReturn(true);
        assertStatus(() -> service.create(req("xem sau", null)), HttpStatus.CONFLICT);
    }

    @Test
    void create_overCap_is409() {
        login(USER);
        when(collectionRepository.countByUserId(USER)).thenReturn((long) VideoCollectionServiceImpl.MAX_COLLECTIONS_PER_USER);
        assertStatus(() -> service.create(req("x", null)), HttpStatus.CONFLICT);
    }

    @Test
    void create_raceOnUniqueIndex_is409() {
        login(USER);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("dup")).when(collectionRepository).saveAndFlush(any());
        assertStatus(() -> service.create(req("x", null)), HttpStatus.CONFLICT);
    }

    // ---- update / delete ----

    @Test
    void update_defaultCollection_is400() {
        login(USER);
        VideoCollection def = stored(USER, CollectionVisibility.PRIVATE, true);
        assertStatus(() -> service.update(def.getId(), req("new", null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.delete(def.getId()), HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_foreignPrivate_is404() {
        login(USER);
        VideoCollection other = stored(OTHER, CollectionVisibility.PRIVATE, false);
        assertStatus(() -> service.update(other.getId(), req("x", null)), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.delete(other.getId()), HttpStatus.NOT_FOUND);
    }

    @Test
    void update_makePublicByNonAdmin_is403() {
        login(USER);
        VideoCollection mine = stored(USER, CollectionVisibility.PRIVATE, false);
        assertStatus(() -> service.update(mine.getId(), req(null, CollectionVisibility.PUBLIC)), HttpStatus.FORBIDDEN);
    }

    @Test
    void update_anotherAdminsPublicCollection_isAllowed() {
        login(USER);
        role(USER, UserRole.ADMIN);
        VideoCollection pub = stored(OTHER, CollectionVisibility.PUBLIC, false);

        CollectionDto dto = service.update(pub.getId(), req("Renamed", null));

        assertThat(dto.getName()).isEqualTo("Renamed");
        assertThat(dto.getVisibility()).isEqualTo(CollectionVisibility.PUBLIC);
        verify(collectionRepository).existsByUserIdAndNameIgnoreCaseAndIdNot(OTHER, "Renamed", pub.getId());
        verify(collectionRepository).existsByVisibilityAndNameIgnoreCaseAndIdNot(CollectionVisibility.PUBLIC, "Renamed", pub.getId());
    }

    @Test
    void publicCollection_isNotManageableByNonAdmins_evenItsDemotedOwner() {
        login(USER);
        VideoCollection pub = stored(USER, CollectionVisibility.PUBLIC, false);
        assertStatus(() -> service.delete(pub.getId()), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.addVideo(pub.getId(), 5L), HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_ownPrivate() {
        login(USER);
        VideoCollection mine = stored(USER, CollectionVisibility.PRIVATE, false);
        service.delete(mine.getId());
        verify(collectionRepository).delete(mine);
    }

    // ---- items ----

    @Test
    void addVideo_hiddenVideo_is404() {
        login(USER);
        VideoCollection mine = stored(USER, CollectionVisibility.PRIVATE, false);
        when(videoSourceRepository.existsByIdAndIsHideAndStatus(5L, false, VideoStatus.PUBLISHED)).thenReturn(false);

        assertStatus(() -> service.addVideo(mine.getId(), 5L), HttpStatus.NOT_FOUND);
        verify(itemRepository, never()).addItem(any(), any());
    }

    @Test
    void addVideo_full_is409() {
        login(USER);
        VideoCollection mine = stored(USER, CollectionVisibility.PRIVATE, false);
        when(videoSourceRepository.existsByIdAndIsHideAndStatus(5L, false, VideoStatus.PUBLISHED)).thenReturn(true);
        when(itemRepository.countItems(mine.getId())).thenReturn((long) VideoCollectionServiceImpl.MAX_ITEMS_PER_COLLECTION);

        assertStatus(() -> service.addVideo(mine.getId(), 5L), HttpStatus.CONFLICT);
    }

    @Test
    void addVideo_idempotent_touchesOnlyWhenInserted() {
        login(USER);
        VideoCollection mine = stored(USER, CollectionVisibility.PRIVATE, false);
        when(videoSourceRepository.existsByIdAndIsHideAndStatus(5L, false, VideoStatus.PUBLISHED)).thenReturn(true);
        when(itemRepository.addItem(mine.getId(), 5L)).thenReturn(1, 0);

        service.addVideo(mine.getId(), 5L);
        service.addVideo(mine.getId(), 5L);

        verify(collectionRepository).save(mine); // once: the second add was a no-op
        assertThat(mine.getUpdatedAt()).isNotNull();
    }

    @Test
    void removeVideo_missingItem_isNoop() {
        login(USER);
        VideoCollection mine = stored(USER, CollectionVisibility.PRIVATE, false);
        when(itemRepository.removeItem(mine.getId(), 5L)).thenReturn(0);

        service.removeVideo(mine.getId(), 5L);

        verify(collectionRepository, never()).save(any());
    }

    @Test
    void removeVideo_existingItem_touches() {
        login(USER);
        VideoCollection mine = stored(USER, CollectionVisibility.PRIVATE, false);
        when(itemRepository.removeItem(mine.getId(), 5L)).thenReturn(1);

        service.removeVideo(mine.getId(), 5L);

        verify(collectionRepository).save(mine);
    }

    @Test
    void membership_returnsContainingCollectionIds() {
        login(USER);
        VideoCollection a = stored(USER, CollectionVisibility.PRIVATE, true);
        VideoCollection b = stored(USER, CollectionVisibility.PRIVATE, false);
        when(collectionRepository.findManageable(USER, false)).thenReturn(List.of(a, b));
        when(itemRepository.findCollectionIdsContaining(5L, List.of(a.getId(), b.getId())))
                .thenReturn(List.of(b.getId().toString()));

        assertThat(service.getMembership(5L)).containsExactly(b.getId());
        assertThat(service.getMembership(null)).isEmpty();
    }

    @Test
    void membership_requiresLogin() {
        assertStatus(() -> service.getMembership(5L), HttpStatus.UNAUTHORIZED);
    }

    // ---- projections ----

    private static VideoCollectionItemRepository.ItemCounts counts(UUID id, long visible, long unavailable) {
        return new VideoCollectionItemRepository.ItemCounts() {
            public String getCollectionId() { return id.toString(); }
            public Long getVisibleCount() { return visible; }
            public Long getUnavailableCount() { return unavailable; }
        };
    }

    private static VideoCollectionItemRepository.Cover cover(UUID id, Long videoId, String fileId) {
        return new VideoCollectionItemRepository.Cover() {
            public String getCollectionId() { return id.toString(); }
            public Long getVideoId() { return videoId; }
            public String getFileId() { return fileId; }
        };
    }
}
