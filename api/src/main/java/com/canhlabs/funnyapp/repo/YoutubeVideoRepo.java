package com.canhlabs.funnyapp.repo;

import com.canhlabs.funnyapp.domain.YouTubeVideo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface YoutubeVideoRepo extends JpaRepository<YouTubeVideo, Long> {
    boolean existsByVideoId(String videoId);
    List<YouTubeVideo> findByVideoIdIn(List<String> videoIds);
    List<YouTubeVideo> findAllBySource(String source);

    Optional<YouTubeVideo> findByVideoId(String videoId);
}