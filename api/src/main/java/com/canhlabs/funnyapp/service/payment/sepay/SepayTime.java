package com.canhlabs.funnyapp.service.payment.sepay;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** SePay reports bank time as "yyyy-MM-dd HH:mm:ss" in Vietnam time. */
final class SepayTime {

    static final ZoneId BANK_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter BANK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SepayTime() {
    }

    /** Null when absent or malformed. */
    static Instant parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), BANK_TIME).atZone(BANK_ZONE).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static String format(Instant instant) {
        return LocalDateTime.ofInstant(instant, BANK_ZONE).format(BANK_TIME);
    }
}
