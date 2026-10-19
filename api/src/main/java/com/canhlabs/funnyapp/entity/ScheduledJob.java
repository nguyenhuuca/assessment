package com.canhlabs.funnyapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** The schedule of one AppScheduler job; the job code itself lives in {@code JobRegistry}. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scheduled_job")
public class ScheduledJob {
    @Id
    @Column(name = "name", length = 64)
    private String name;

    /** Spring 6-field cron. */
    @Column(name = "cron", nullable = false, length = 64)
    private String cron;

    /** Null = JVM default zone. */
    @Column(name = "zone", length = 64)
    private String zone;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "description")
    private String description;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
