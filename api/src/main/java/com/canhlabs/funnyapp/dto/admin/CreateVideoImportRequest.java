package com.canhlabs.funnyapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateVideoImportRequest {
    private List<Item> items;
    /** Null means run as soon as possible. */
    private Instant scheduledAt;
    /** FEED (default when null) or COLLECTION_ONLY; kept as text so an unknown value maps to INVALID_VISIBILITY. */
    private String visibility;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String url;
        private String title;
    }
}
