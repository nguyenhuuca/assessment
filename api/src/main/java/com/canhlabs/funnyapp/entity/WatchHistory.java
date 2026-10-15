package com.canhlabs.funnyapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per (user, video): when it was last watched and the last reported position.
 * Writes go through WatchHistoryRepository native statements (ADR-0021).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "watch_history")
public class WatchHistory {

    @EmbeddedId
    private WatchHistoryId id;

    @Column(name = "watched_at", nullable = false)
    private Instant watchedAt;

    @Column(name = "position_sec", nullable = false)
    private int positionSec;

    @Column(name = "duration_sec")
    private Integer durationSec;
}
