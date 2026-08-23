package com.planshift.experiment;

import com.planshift.datagen.DataGenConfig;
import com.planshift.datagen.DataGeneratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the full experiment pipeline (data generation, baseline
 * vs. candidate benchmarking, EXPLAIN capture, plan comparison, persistence)
 * against a real, throwaway PostgreSQL container.
 */
@SpringBootTest
@Testcontainers
class IndexExperimentIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private DataGeneratorService dataGeneratorService;

    @Autowired
    private IndexExperimentService experimentService;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private IndexChangeService indexChangeService;

    @Test
    void runsFullExperimentAndPersistsResultsForEveryQuery() {
        dataGeneratorService.generate(new DataGenConfig(42L, 500, 50, 2000, 4));

        long experimentId = experimentService.runDefault();
        Experiment experiment = experimentRepository.findById(experimentId).orElseThrow();

        assertThat(experiment.status()).isEqualTo(ExperimentStatus.COMPLETED);
        assertThat(experiment.queryResults()).hasSize(10);
        assertThat(experiment.overallPercentageChange()).isNotNull();

        // the experiment leaves the index in place (the last configuration it ran)
        assertThat(indexChangeService.customerIdIndexExists()).isTrue();
    }

    @Test
    void orderHistoryQueryImprovesWithIndex() {
        dataGeneratorService.generate(new DataGenConfig(42L, 500, 50, 2000, 4));

        long experimentId = experimentService.runDefault();
        Experiment experiment = experimentRepository.findById(experimentId).orElseThrow();

        ExperimentQueryResult orderHistory = experiment.queryResults().stream()
                .filter(r -> r.queryId().equals("Q3_ORDER_HISTORY_LOOKUP"))
                .findFirst()
                .orElseThrow();

        // This is the query the customer_id index directly targets. We deliberately don't assert
        // candidateMedianMs <= baselineMedianMs here: at this test's small scale both are
        // sub-millisecond, where timing is noisy enough that a near-tie can occasionally flip either
        // way (this test caught exactly that flake once -- see git history). The real, deterministic
        // signal is the plan itself: the scan strategy must have actually changed once the index exists.
        assertThat(orderHistory.baselinePlanJson()).contains("Seq Scan");
        assertThat(orderHistory.candidatePlanJson()).containsAnyOf("Index Scan", "Bitmap Index Scan");
        assertThat(orderHistory.planDiffSummary()).contains("Scan/execution strategy changed");
    }

    @Test
    void unrelatedQueriesRemainUnaffectedByTheIndex() {
        dataGeneratorService.generate(new DataGenConfig(42L, 500, 50, 2000, 4));

        long experimentId = experimentService.runDefault();
        Experiment experiment = experimentRepository.findById(experimentId).orElseThrow();

        ExperimentQueryResult groupByStatus = experiment.queryResults().stream()
                .filter(r -> r.queryId().equals("Q7_GROUP_BY_STATUS"))
                .findFirst()
                .orElseThrow();

        // this query never filters on orders.customer_id, so its plan shape should not change
        assertThat(groupByStatus.planDiffSummary()).isEqualTo("No change in query plan structure.");
    }
}
