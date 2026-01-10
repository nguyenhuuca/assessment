package com.canhlabs.funnyapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "video_sources")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Data
public class VideoSource extends BaseDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false)
    private Long videoId;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 255)
    private String sourceId;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "credentials_ref", length = 255)
    private String credentialsRef;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String desc;


    @Column(name = "is_hide", nullable = false)
    private boolean isHide = false;

    @Column(name = "thumbnail_path")
    private String thumbnailPath;

    @Column(name = "video_type", length = 50)
    private String videoType;


}
