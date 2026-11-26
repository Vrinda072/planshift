package com.planshift.impact;

import com.planshift.experiment.IndexSpec;
import com.planshift.lock.DatabaseLock;
import com.planshift.querybuilder.QuerySpec;
import com.planshift.schema.SchemaService;
import com.planshift.schema.TableInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Against a Postgres that has HypoPG. ImpactPredictionIT covers the estimates made without it. */
@SpringBootTest
@Testcontainers
class PlannerEstimateIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName
            .parse(new ImageFromDockerfile("planshift-postgres-hypopg-test", false)
                    .withDockerfile(Path.of("../db/Dockerfile")).get())
            .asCompatibleSubstituteFor("postgres"));

    @Autowired
    private ImpactPredictionService predictions;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private DatabaseLock databaseLock;

    @Autowired
    private JdbcTemplate jdbc;

    private static final QuerySpec QUERY = new QuerySpec("planner_probe", null, null, null, null,
            "a", "=", "7", null, null, 100);

    @BeforeEach
    void table() {
        jdbc.execute("DROP TABLE IF EXISTS planner_probe");
        jdbc.execute("CREATE TABLE planner_probe (a INT, b INT)");
        jdbc.execute("INSERT INTO planner_probe SELECT g % 5000, g FROM generate_series(1, 100000) g");
        jdbc.execute("ANALYZE planner_probe");
    }

    private PredictedImpact predict(String column) {
        return predictions.predict(QUERY, IndexSpec.of(column));
    }

    @Test
    void asksThePlannerAndLeavesNothingBehind() {
        PredictedImpact useful = predict("a");
        PredictedImpact useless = predict("b");

        assertThat(useful.method()).isEqualTo("planner");
        assertThat(useful.predictedPercentageChange()).isLessThan(-50);
        assertThat(useful.note()).contains("would use this index");
        assertThat(useless.method()).isEqualTo("planner");
        assertThat(useless.predictedPercentageChange()).isZero();
        assertThat(useless.note()).contains("wouldn't use");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'planner_probe'",
                Integer.class)).isZero();
        // Whichever pooled session answers next must not still believe in the index.
        for (int i = 0; i < 12; i++) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hypopg_list_indexes", Integer.class)).isZero();
        }
    }

    @Test
    void doesNotAskWhileSomethingElseIsChangingTheDatabase() {
        try (DatabaseLock.Held ignored = databaseLock.acquire()) {
            PredictedImpact impact = predict("a");

            assertThat(impact.method()).isNotEqualTo("planner");
            assertThat(impact.note()).startsWith("The planner wasn't asked because");
        }
        assertThat(predict("a").method()).isEqualTo("planner");
    }

    @Test
    void theExtensionsViewsAreNotOfferedAsTables() {
        predict("a");

        assertThat(schemaService.listTables()).extracting(TableInfo::tableName)
                .contains("planner_probe")
                .noneMatch(name -> name.startsWith("hypopg"));
        assertThat(schemaService.tableExists("hypopg_list_indexes")).isFalse();
        assertThat(schemaService.tableExists("planner_probe")).isTrue();
    }
}
