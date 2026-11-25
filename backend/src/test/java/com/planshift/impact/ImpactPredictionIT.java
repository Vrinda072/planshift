package com.planshift.impact;

import com.planshift.experiment.ExperimentRepository;
import com.planshift.experiment.IndexSpec;
import com.planshift.querybuilder.QuerySpec;
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
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Testcontainers
class ImpactPredictionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ImpactPredictionService predictions;

    @Autowired
    private ExperimentRepository experiments;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void estimatesFromTheWholeIndexAndOnlyTrustsComparableHistory() {
        jdbc.execute("DROP TABLE IF EXISTS predict_probe");
        jdbc.execute("CREATE TABLE predict_probe (a INT, b INT)");
        // Large enough that Postgres plans a parallel scan, where per-worker row counts would mislead.
        jdbc.execute("INSERT INTO predict_probe SELECT g % 10, g FROM generate_series(1, 400000) g");
        jdbc.execute("ANALYZE predict_probe");
        QuerySpec query = new QuerySpec("predict_probe", null, null, null, null, "a", "=", "3", null, null, 100);

        PredictedImpact wrongOrder = predictions.predict(query, new IndexSpec(List.of("b", "a")));
        assertThat(wrongOrder.method()).isEqualTo("structure");
        assertThat(wrongOrder.predictedPercentageChange()).isZero();

        PredictedImpact noHistory = predictions.predict(query, new IndexSpec(List.of("a", "b")));
        assertThat(noHistory.method()).isEqualTo("heuristic");
        assertThat(noHistory.confidence()).isEqualTo("low");
        assertThat(noHistory.selectivity()).isCloseTo(0.1, within(0.02));

        for (double change : new double[] {-40, -60, -50}) {
            long id = experiments.insertExperiment("CUSTOM_QUERY", 0, 0, 42L, 5, 0.2, "predict_probe", "a");
            experiments.saveQueryFeatures(id, "FILTER", "EQUALITY", 0.12, false);
            jdbc.update("INSERT INTO experiment_query_results (experiment_id, query_id, query_name, baseline_median_ms, "
                    + "candidate_median_ms, absolute_change_ms, percentage_change, status) "
                    + "VALUES (?, 'q', 'q', 1, 1, 0, ?, 'IMPROVED')", id, change);
            experiments.completeExperiment(id, change);
        }

        PredictedImpact fromHistory = predictions.predict(query, new IndexSpec(List.of("a", "b")));
        assertThat(fromHistory.method()).isEqualTo("historical");
        assertThat(fromHistory.sampleSize()).isEqualTo(3);
        assertThat(fromHistory.predictedPercentageChange()).isEqualTo(-50.0);
    }
}
