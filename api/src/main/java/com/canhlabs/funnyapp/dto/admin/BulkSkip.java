package com.canhlabs.funnyapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row a bulk action refused to touch, with the reason code the UI can translate. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkSkip {
    /** Resource id as text so Long and UUID resources share one shape. */
    private String id;
    private String code;
    private String message;
}
