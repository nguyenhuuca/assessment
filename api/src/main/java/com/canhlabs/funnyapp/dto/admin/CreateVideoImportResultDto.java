package com.canhlabs.funnyapp.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVideoImportResultDto {
    private List<LineResult> results;

    /** Either jobId (accepted) or errorCode (INVALID_URL, DUPLICATE_ACTIVE). */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LineResult {
        private int line;
        private Long jobId;
        private String errorCode;
    }
}
