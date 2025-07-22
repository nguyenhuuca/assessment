package com.canhlabs.funnyapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "video_access_stats")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class VideoAccessStats extends BaseDomain {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "video_id")
    private String videoId;

    @Column(name = "hit_count")
    private int hitCount;

    @UpdateTimestamp
    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    public void incrementHit() {
        this.hitCount++;
        this.lastAccessedAt = Instant.now();
    }

}