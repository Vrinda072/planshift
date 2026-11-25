package com.planshift.experiment;

import com.planshift.benchmark.BenchmarkService;
import com.planshift.schema.IndexNaming;
import com.planshift.workload.QueryCategory;
import com.planshift.workload.WorkloadQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

/** Experiments must never see each other's indexes, and must leave the database as they found it. */
@SpringBootTest(properties = "planshift.experiments.queue-capacity=1")
@Testcontainers
class ExperimentIsolationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @MockitoSpyBean
    private BenchmarkService benchmarkService;

    @Autowired
    private IndexExperimentService experimentService;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private ExperimentRecovery recovery;

    @Autowired
    private IndexChangeService indexChangeService;

    @Autowired
    private JdbcTemplate jdbc;

    private static final WorkloadQuery QUERY = new WorkloadQuery(
            "ISOLATION", "isolation probe", QueryCategory.values()[0], "probe", "SELECT * FROM iso_probe WHERE a = 7");

    @BeforeEach
    void probeTable() {
        jdbc.execute("DROP TABLE IF EXISTS iso_probe");
        jdbc.execute("CREATE TABLE iso_probe (a INT, b INT)");
        jdbc.execute("INSERT INTO iso_probe SELECT g, g % 9 FROM generate_series(1, 2000) g");
        // Indexes that existed beforehand, one of them using the naming style older versions dropped on sight.
        jdbc.execute("CREATE INDEX my_own_idx ON iso_probe (b)");
        jdbc.execute("CREATE INDEX idx_planshift_iso_probe_b ON iso_probe (b)");
    }

    private List<String> indexNames() {
        return jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'iso_probe' ORDER BY indexname", String.class);
    }

    private List<String> temporaryIndexes() {
        return indexNames().stream().filter(IndexNaming::isTemporary).toList();
    }

    private long start(String column) {
        return experimentService.startAsync(List.of(QUERY), Map.of(), 2, 0.2, "iso_probe",
                IndexSpec.of(column), "CUSTOM_QUERY");
    }

    private ExperimentStatus status(long id) {
        return experimentRepository.findById(id).orElseThrow().status();
    }

    private ExperimentStatus awaitFinished(long id) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (status(id) == ExperimentStatus.QUEUED || status(id) == ExperimentStatus.RUNNING) {
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(25);
        }
        return status(id);
    }

    @Test
    void secondExperimentWaitsAndNeverSeesTheFirstOnesIndex() throws Exception {
        CountDownLatch firstIsMeasuring = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<List<String>> seenWhileMeasuring = new CopyOnWriteArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            seenWhileMeasuring.add(new ArrayList<>(temporaryIndexes()));
            // Hold the first experiment while it owns its index, the moment an overlap would do damage.
            if (calls.incrementAndGet() == 2) {
                firstIsMeasuring.countDown();
                assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
            }
            return invocation.callRealMethod();
        }).when(benchmarkService).executeRepeated(any(), any(), anyInt());

        long first = start("a");
        assertThat(firstIsMeasuring.await(20, TimeUnit.SECONDS)).isTrue();
        long second = start("a");
        Thread.sleep(300);

        assertThat(status(first)).isEqualTo(ExperimentStatus.RUNNING);
        assertThat(status(second)).isEqualTo(ExperimentStatus.QUEUED);
        assertThat(temporaryIndexes()).containsExactly(IndexNaming.experimentName(first));
        // The queue holds one job here, so a third is refused and leaves no row behind.
        int rows = experimentRepository.findAllSummaries().size();
        assertThatThrownBy(() -> start("a")).isInstanceOf(QueueFullException.class);
        assertThat(experimentRepository.findAllSummaries()).hasSize(rows);

        release.countDown();
        assertThat(awaitFinished(first)).isEqualTo(ExperimentStatus.COMPLETED);
        assertThat(awaitFinished(second)).isEqualTo(ExperimentStatus.COMPLETED);

        // Each run measures before, with its own index, and before again.
        assertThat(seenWhileMeasuring).containsExactly(
                List.of(), List.of(IndexNaming.experimentName(first)), List.of(),
                List.of(), List.of(IndexNaming.experimentName(second)), List.of());
        assertThat(indexNames()).containsExactly("idx_planshift_iso_probe_b", "my_own_idx");
    }

    @Test
    void failureAfterIndexCreationRemovesOnlyTheExperimentsIndex() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> presentAtFailure = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) {
                presentAtFailure.addAll(temporaryIndexes());
                throw new IllegalStateException("boom while measuring the candidate");
            }
            return invocation.callRealMethod();
        }).when(benchmarkService).executeRepeated(any(), any(), anyInt());

        long id = start("a");

        assertThat(awaitFinished(id)).isEqualTo(ExperimentStatus.FAILED);
        assertThat(presentAtFailure).containsExactly(IndexNaming.experimentName(id));
        assertThat(indexNames()).containsExactly("idx_planshift_iso_probe_b", "my_own_idx");
    }

    @Test
    void controlRunChangesNothingAndKeepsEverySample() throws Exception {
        List<List<String>> seen = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            seen.add(new ArrayList<>(temporaryIndexes()));
            return invocation.callRealMethod();
        }).when(benchmarkService).executeRepeated(any(), any(), anyInt());

        long id = experimentService.startControlAsync(List.of(QUERY), Map.of(), 5, 0.0, "iso_probe");

        assertThat(awaitFinished(id)).isEqualTo(ExperimentStatus.COMPLETED);
        assertThat(seen).hasSize(3).allMatch(List::isEmpty);
        Experiment experiment = experimentRepository.findById(id).orElseThrow();
        assertThat(experiment.experimentType()).isEqualTo(Experiment.CONTROL_TYPE);
        assertThat(experiment.targetColumn()).isNull();
        Timing timing = experiment.queryResults().get(0).timing();
        assertThat(timing.baselineSamplesMs()).hasSize(5);
        assertThat(timing.candidateSamplesMs()).hasSize(5);
        assertThat(timing.baselinePlanStats().sharedHitBlocks() + timing.baselinePlanStats().sharedReadBlocks())
                .isPositive();
        // Even with a zero threshold, nothing a control run finds is listed as a regression.
        assertThat(experimentRepository.findAllRegressions()).noneMatch(r -> r.experimentId() == id);
        assertThat(indexNames()).containsExactly("idx_planshift_iso_probe_b", "my_own_idx");
    }

    @Test
    void restartFailsInterruptedRunsAndRemovesTheIndexTheyOwned() {
        long id = experimentRepository.insertExperiment("CUSTOM_QUERY", 0, 0, 42L, 2, 0.2, "iso_probe", "a");
        experimentRepository.markRunning(id);
        indexChangeService.createExperimentIndex(id, "iso_probe", IndexSpec.of("a"));
        assertThat(temporaryIndexes()).hasSize(1);

        recovery.recover();

        assertThat(status(id)).isEqualTo(ExperimentStatus.FAILED);
        assertThat(indexNames()).containsExactly("idx_planshift_iso_probe_b", "my_own_idx");
    }

    @Test
    void legacyCleanupDropsOnlyIndexesThatAStoredExperimentCreated() {
        long id = experimentRepository.insertExperiment("CUSTOM_QUERY", 0, 0, 42L, 2, 0.2, "iso_probe", "a");
        experimentRepository.failExperiment(id);
        jdbc.update("UPDATE experiments SET legacy_index_pending = TRUE WHERE experiment_id = ?", id);
        jdbc.execute("CREATE INDEX idx_planshift_iso_probe_a ON iso_probe (a)");

        recovery.recover();

        assertThat(indexNames()).containsExactly("idx_planshift_iso_probe_b", "my_own_idx");
    }
}
