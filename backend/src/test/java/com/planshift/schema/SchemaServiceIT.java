package com.planshift.schema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SchemaServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private JdbcTemplate jdbc;

    private TableInfo table(String name) {
        return schemaService.listTables().stream()
                .filter(t -> t.tableName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private boolean indexed(TableInfo table, String column) {
        return table.columns().stream()
                .filter(c -> c.columnName().equals(column))
                .findFirst()
                .orElseThrow()
                .indexed();
    }

    @Test
    void reportsRowCountAndIndexedColumns() {
        jdbc.execute("DROP TABLE IF EXISTS schema_probe");
        jdbc.execute("CREATE TABLE schema_probe (id BIGINT PRIMARY KEY, city TEXT, score INT)");
        jdbc.execute("INSERT INTO schema_probe SELECT g, 'c' || (g % 5), g FROM generate_series(1, 40) g");
        jdbc.execute("CREATE INDEX my_city_idx ON schema_probe (city)");
        jdbc.execute("CREATE INDEX idx_planshift_schema_probe_score ON schema_probe (score)");

        TableInfo info = table("schema_probe");

        assertThat(info.rowEstimate()).isEqualTo(40);
        assertThat(indexed(info, "id")).isTrue();
        assertThat(indexed(info, "city")).isTrue();
        // the index PLANSHIFT itself manages doesn't count: the baseline drops it
        assertThat(indexed(info, "score")).isFalse();
    }

    @Test
    void onlyTheLeadingColumnOfACompositeIndexCountsAsIndexed() {
        jdbc.execute("DROP TABLE IF EXISTS schema_probe_composite");
        jdbc.execute("CREATE TABLE schema_probe_composite (a INT, b INT)");
        jdbc.execute("CREATE INDEX idx_schema_probe_composite ON schema_probe_composite (a, b)");

        TableInfo info = table("schema_probe_composite");

        assertThat(indexed(info, "a")).isTrue();
        assertThat(indexed(info, "b")).isFalse();
        assertThat(info.rowEstimate()).isZero();
    }
}
