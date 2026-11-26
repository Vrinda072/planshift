package com.planshift.experiment;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.SqlParameterValue;

import java.sql.Types;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DisplaySqlTest {

    @Test
    void writesValuesInWithQuotesEscaped() {
        assertThat(DisplaySql.of("SELECT * FROM t WHERE a = :filterValue",
                Map.of("filterValue", new SqlParameterValue(Types.OTHER, "O'Brien $1"))))
                .isEqualTo("SELECT * FROM t WHERE a = 'O''Brien $1'");
        assertThat(DisplaySql.of("SELECT :id, now()::date, :missing", Map.of("id", 7)))
                .isEqualTo("SELECT 7, now()::date, :missing");
    }
}
