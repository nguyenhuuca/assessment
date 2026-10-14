package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.VideoCollection;
import com.canhlabs.funnyapp.enums.CollectionVisibility;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoCollectionRepository extends JpaRepository<VideoCollection, UUID> {

    /** The caller's own collections, plus every PUBLIC one when {@code includePublic} (admins). Default first. */
    @Query("SELECT c FROM VideoCollection c WHERE c.userId = :userId "
            + "OR (:includePublic = true AND c.visibility = com.canhlabs.funnyapp.enums.CollectionVisibility.PUBLIC) "
            + "ORDER BY c.isDefault DESC, c.updatedAt DESC")
    List<VideoCollection> findManageable(@Param("userId") Long userId, @Param("includePublic") boolean includePublic);

    /** Row lock for writes: serializes concurrent adds so the per-collection item cap holds. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM VideoCollection c WHERE c.id = :id")
    Optional<VideoCollection> findByIdForUpdate(@Param("id") UUID id);

    /** Locks the owner's users row so concurrent creates cannot both pass the per-user collection cap. */
    @Query(value = "SELECT id FROM users WHERE id = :userId FOR UPDATE", nativeQuery = true)
    Optional<Long> lockOwner(@Param("userId") Long userId);

    List<VideoCollection> findTop100ByVisibilityOrderByUpdatedAtDesc(CollectionVisibility visibility);

    Optional<VideoCollection> findByUserIdAndIsDefaultTrue(Long userId);

    /** Race-safe: the partial unique index (user_id) WHERE is_default turns a concurrent second insert into a no-op. */
    @Modifying
    @Query(value = "INSERT INTO video_collections (user_id, name, visibility, is_default) "
            + "VALUES (:userId, :name, 'PRIVATE', true) ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertDefaultIfMissing(@Param("userId") Long userId, @Param("name") String name);

    long countByUserId(Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, UUID id);

    boolean existsByVisibilityAndNameIgnoreCase(CollectionVisibility visibility, String name);

    boolean existsByVisibilityAndNameIgnoreCaseAndIdNot(CollectionVisibility visibility, String name, UUID id);
}
