package com.canhlabs.funnyapp.utils;

import java.util.regex.Pattern;

/** Makes a title safe to use as a Drive file name and as the ingested video title (ADR-0019 D5). */
public final class TitleSanitizer {

    public static final int MAX_LENGTH = 120;

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]");
    private static final Pattern FORBIDDEN = Pattern.compile("[/\\\\:*?\"<>|]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TitleSanitizer() {
    }

    /** Never returns null; the result may be empty when nothing usable is left. */
    public static String sanitize(String title) {
        if (title == null) {
            return "";
        }
        String s = WHITESPACE.matcher(title).replaceAll(" ");
        s = CONTROL.matcher(s).replaceAll("");
        s = FORBIDDEN.matcher(s).replaceAll("");
        s = WHITESPACE.matcher(s).replaceAll(" ").trim();
        if (s.length() > MAX_LENGTH) {
            int end = MAX_LENGTH;
            if (Character.isHighSurrogate(s.charAt(end - 1))) {
                end--;
            }
            s = s.substring(0, end).trim();
        }
        return s;
    }
}
