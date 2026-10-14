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
public class ApproveSuggestionRequest {
    /** Optional title override applied to the import job. */
    @Size(max = 200)
    private String title;
}
