package com.canhlabs.funnyapp.dto.suggestion;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubmitSuggestionRequest {
    /** Validated by the service (allowlist + normalization); a bad value is error.suggestion.invalidUrl. */
    private String url;
    @Size(max = 200)
    private String title;
    @Size(max = 300)
    private String note;
}
