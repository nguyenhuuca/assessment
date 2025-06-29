package com.canhlabs.funnyapp.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.InputStream;

@Getter
@Setter
@Builder
public class StreamChunkResult {
    private final InputStream stream;
    private final long actualStart;
    private final long actualEnd;
}
