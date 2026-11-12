package com.canhlabs.funnyapp.dto.notification;

import com.canhlabs.funnyapp.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private UUID id;
    private NotificationType type;
    private String videoId;
    private UUID commentId;
    private String actorDisplay;
    private int actorCount;
    private String snippet;
    private String reason;
    /** Payment notifications (admin): order code, triggering transfer, order total paid so far, donor name. */
    private String orderCode;
    private String amount;
    private String paidAmount;
    private String donorName;
    private boolean read;
    private Instant updatedAt;
}
