package com.planshift.experiment;

import com.planshift.schema.SchemaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class IndexChangeServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private IndexChangeService indexChangeService;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private JdbcTemplate jdbc;

    private void probeTable() {
        jdbc.execute("DROP TABLE IF EXISTS idx_probe");
        jdbc.execute("CREATE TABLE idx_probe (a INT, b INT, c TEXT)");
        jdbc.execute("INSERT INTO idx_probe SELECT g, g % 7, 'x' || g FROM generate_series(1, 100) g");
    }

    private List<String> definitions() {
        return jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename = 'idx_probe'", String.class);
    }

    @Test
    void createsAndDropsCompositeBtree() {
        probeTable();
        IndexSpec spec = new IndexSpec(List.of("a", "b"));
        indexChangeService.addIndex("idx_probe", spec);
        assertThat(definitions()).singleElement().asString().contains("USING btree (a, b)");

        indexChangeService.dropIndex("idx_probe", spec);
        assertThat(definitions()).isEmpty();
    }

    @Test
    void managedCompositeIndexDoesNotCountAsAlreadyIndexed() {
        probeTable();
        indexChangeService.addIndex("idx_probe", new IndexSpec(List.of("a", "b")));
        boolean aIndexed = schemaService.listTables().stream()
                .filter(t -> t.tableName().equals("idx_probe")).findFirst().orElseThrow()
                .columns().stream().filter(c -> c.columnName().equals("a")).findFirst().orElseThrow().indexed();
        assertThat(aIndexed).isFalse();
    }

    @Test
    void rejectsUnknownColumnsBeforeRunningDdl() {
        probeTable();
        assertThatThrownBy(() -> indexChangeService.addIndex("idx_probe", new IndexSpec(List.of("a", "nope"))))
                .hasMessageContaining("Unknown column");
    }

    @Test
    void baselineClearsLeftoverManagedIndexesButKeepsUserOnes() {
        probeTable();
        jdbc.execute("CREATE INDEX my_own_idx ON idx_probe (a)");
        indexChangeService.addIndex("idx_probe", IndexSpec.of("a"));
        indexChangeService.addIndex("idx_probe", new IndexSpec(List.of("a", "b")));
        indexChangeService.addIndex("idx_probe", IndexSpec.of("c"));

        indexChangeService.dropManagedIndexesLeadingWith("idx_probe", new IndexSpec(List.of("a", "b")));

        assertThat(definitions()).hasSize(2)
                .anyMatch(d -> d.contains("my_own_idx"))
                .anyMatch(d -> d.contains("(c)"));
    }
}
