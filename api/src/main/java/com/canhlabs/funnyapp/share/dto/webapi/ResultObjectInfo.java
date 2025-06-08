package com.canhlabs.funnyapp.share.dto.webapi;

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
