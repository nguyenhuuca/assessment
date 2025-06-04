package com.canhlabs.funnyapp.domain;

import com.canhlabs.funnyapp.share.enums.Status;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_email_request")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEmailRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "invited_by_user_id")
    private Long invitedByUserId;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 50, nullable = false)
    private String purpose;

    @Column(name = "metadata")
    private String metadata;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        if (this.status == null) {
            this.status = Status.PENDING;
        }
        if (this.purpose == null) {
            this.purpose = "JOIN_SYSTEM";
        }
    }
}