package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.entity.VideoComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface VideoCommentRepository extends JpaRepository<VideoComment, UUID> {
    List<VideoComment> findByVideoIdOrderByCreatedAtAsc(String videoId);

    List<VideoComment> findByVideoIdAndParentIdIsNullOrderByCreatedAtAsc(String videoId);

    List<VideoComment> findByParentId(String parentId);

    @Query("select c from VideoComment c where c.videoId = :videoId order by c.createdAt asc")
    List<VideoComment> findAllByVideoIdOrdered(String videoId);
}
