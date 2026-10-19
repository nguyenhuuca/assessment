package com.canhlabs.funnyapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** PATCH body: only the fields that are sent change; {@code zone: ""} clears the zone. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScheduledJobRequest {
    private String cron;
    private String zone;
    private Boolean enabled;
}
