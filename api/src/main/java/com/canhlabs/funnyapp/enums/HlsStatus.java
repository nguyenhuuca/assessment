package com.canhlabs.funnyapp.enums;

/** State of a video's HLS rendition (ADR-0031, BR-3). */
public enum HlsStatus {
    NONE,
    PENDING,
    PROCESSING,
    READY,
    ERROR
}
