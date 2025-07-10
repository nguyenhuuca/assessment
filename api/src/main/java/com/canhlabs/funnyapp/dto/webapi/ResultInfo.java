package com.canhlabs.funnyapp.dto.webapi;

import com.canhlabs.funnyapp.enums.ResultStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Using to return from any controller
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class ResultInfo {
    private ResultStatus status;
    @Builder.Default
    private String message = "Executed success";
}