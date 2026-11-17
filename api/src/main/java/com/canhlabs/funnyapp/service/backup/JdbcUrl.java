package com.canhlabs.funnyapp.service.backup;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Host, port and database parsed from a {@code jdbc:postgresql://host[:port]/db[?params]} URL. */
public record JdbcUrl(String host, int port, String database) {
    private static final Pattern PATTERN =
            Pattern.compile("^jdbc:postgresql://([^/:?]+)(?::(\\d{1,5}))?/([^/?]+)(?:\\?.*)?$");

    public static JdbcUrl parse(String url) {
        Matcher m = url == null ? null : PATTERN.matcher(url.trim());
        if (m == null || !m.matches()) {
            throw new BackupException(BackupErrorCode.NOT_CONFIGURED, BackupStep.CONFIG,
                    "datasource URL is not a jdbc:postgresql URL");
        }
        int port = m.group(2) == null ? 5432 : Integer.parseInt(m.group(2));
        return new JdbcUrl(m.group(1), port, m.group(3));
    }
}
