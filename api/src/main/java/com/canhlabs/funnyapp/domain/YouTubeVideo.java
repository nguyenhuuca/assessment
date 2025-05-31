package com.canhlabs.funnyapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "youtube_video")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class YouTubeVideo extends BaseDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false, unique = true)
    private String videoId;

    @Column(name = "source")
    private String source;

    @Column(name = "url_link")
    private String urlLink;

    @Column(name = "embed_link")
    private String embedLink;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String desc;


    @Column(name = "up_count")
    private Long upCount;

    @Column(name = "down_count")
    private Long downCount;

}