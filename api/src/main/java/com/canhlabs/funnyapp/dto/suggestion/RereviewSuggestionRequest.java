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
public class RereviewSuggestionRequest {
    /** Optional; replaces the suggestion's note so the admin sees why the user asks again. */
    @Size(max = 300)
    private String message;
}
