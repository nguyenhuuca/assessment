package com.canhlabs.funnyapp.dto.suggestion;

import com.canhlabs.funnyapp.enums.SuggestionRejectReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RejectSuggestionRequest {
    @NotNull
    private SuggestionRejectReason reason;
    /** Required when reason is OTHER. */
    @Size(max = 300)
    private String note;
}
