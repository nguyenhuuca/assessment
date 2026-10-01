package com.canhlabs.funnyapp.dto.reaction;

import com.canhlabs.funnyapp.enums.ReactionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReactionRequest {
    @NotNull
    private ReactionType reaction;
}
