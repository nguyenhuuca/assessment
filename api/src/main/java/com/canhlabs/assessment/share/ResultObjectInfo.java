package com.canhlabs.assessment.share;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class ResultObjectInfo<T> extends ResultInfo {
    private T data;
}
