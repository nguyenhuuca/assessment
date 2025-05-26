package com.canhlabs.funnyapp.share;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class ResultListInfo<T> extends ResultInfo {
    private List<T> data;
}
