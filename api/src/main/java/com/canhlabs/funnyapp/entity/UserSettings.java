package com.canhlabs.funnyapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(name = "user_settings")
public class UserSettings {

    /**
     * PK = FK to users(id). 1:1 relationship — not auto-generated.
     */
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "notify_new_content", nullable = false)
    private boolean notifyNewContent = true;

    @Column(name = "notify_email", nullable = false)
    private boolean notifyEmail = true;

    @Column(name = "default_quality", nullable = false, length = 10)
    private String defaultQuality = "AUTO";

    @Column(name = "incognito_enabled", nullable = false)
    private boolean incognitoEnabled = false;

    @Column(name = "profile_private", nullable = false)
    private boolean profilePrivate = false;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
