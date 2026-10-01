package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.VideoReaction;
import com.canhlabs.funnyapp.entity.VideoReactionId;
import com.canhlabs.funnyapp.enums.ReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VideoReactionRepository extends JpaRepository<VideoReaction, VideoReactionId> {

    /**
     * Group-by projection row: reaction type and its total.
     */
    interface ReactionCount {
        ReactionType getReaction();

        long getTotal();
    }

    @Modifying
    @Query(value = """
            INSERT INTO video_reactions (user_id, video_id, reaction, created_at, updated_at)
            VALUES (:userId, :videoId, :reaction, now(), now())
            ON CONFLICT (user_id, video_id)
            DO UPDATE SET reaction = EXCLUDED.reaction, updated_at = now()
            """, nativeQuery = true)
    void upsert(@Param("userId") Long userId, @Param("videoId") Long videoId, @Param("reaction") String reaction);

    void deleteByIdUserIdAndIdVideoId(Long userId, Long videoId);

    @Query("SELECT r.reaction FROM VideoReaction r WHERE r.id.userId = :userId AND r.id.videoId = :videoId")
    Optional<ReactionType> findReaction(@Param("userId") Long userId, @Param("videoId") Long videoId);

    @Query("SELECT r.reaction AS reaction, COUNT(r) AS total FROM VideoReaction r "
            + "WHERE r.id.videoId = :videoId GROUP BY r.reaction")
    List<ReactionCount> countByReaction(@Param("videoId") Long videoId);
}
