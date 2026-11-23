package com.canhlabs.funnyapp.service.backup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcUrlTest {

    @Test
    void parsesWithPort() {
        assertThat(JdbcUrl.parse("jdbc:postgresql://db.local:6543/funny"))
                .isEqualTo(new JdbcUrl("db.local", 6543, "funny"));
    }

    @Test
    void defaultsPort() {
        assertThat(JdbcUrl.parse("jdbc:postgresql://db.local/funny"))
                .isEqualTo(new JdbcUrl("db.local", 5432, "funny"));
    }

    @Test
    void dropsParams() {
        assertThat(JdbcUrl.parse("jdbc:postgresql://db.local:5432/funny?sslmode=require&prepareThreshold=0"))
                .isEqualTo(new JdbcUrl("db.local", 5432, "funny"));
    }

    @Test
    void parsesIpv4Host() {
        assertThat(JdbcUrl.parse("jdbc:postgresql://10.0.0.5:5433/funny"))
                .isEqualTo(new JdbcUrl("10.0.0.5", 5433, "funny"));
    }

    @Test
    void rejectsNonPostgresAndMalformed() {
        for (String bad : new String[] {"jdbc:mysql://h/db", "jdbc:postgresql://h", "jdbc:postgresql:///db",
                "", null, "postgres://h/db"}) {
            assertThatThrownBy(() -> JdbcUrl.parse(bad))
                    .isInstanceOfSatisfying(BackupException.class, e -> {
                        assertThat(e.getCode()).isEqualTo(BackupErrorCode.NOT_CONFIGURED);
                        assertThat(e.getStep()).isEqualTo(BackupStep.CONFIG);
                    });
        }
    }
}
