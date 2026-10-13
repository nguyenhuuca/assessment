package com.canhlabs.funnyapp.dto.collection;

import com.canhlabs.funnyapp.enums.CollectionVisibility;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class CollectionDto {
    private UUID id;
    private String name;
    private CollectionVisibility visibility;
    /** Getter renamed for JSON: Lombok's isDefault() would otherwise serialize as "default". */
    @Getter(onMethod_ = @JsonProperty("isDefault"))
    private boolean isDefault;
    /** Email local part of the owner; never the full email. */
    private String ownerName;
    /** Visible (not hidden, PUBLISHED) videos only. */
    private long itemCount;
    /** Hidden/unpublished items; null unless the caller can manage the collection. */
    private Long unavailableCount;
    private Long coverVideoId;
    private String coverFileId;
    private boolean canManage;
    private Instant updatedAt;
}
