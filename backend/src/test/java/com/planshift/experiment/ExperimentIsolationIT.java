package com.planshift.experiment;

import com.planshift.benchmark.BenchmarkService;
import com.planshift.schema.IndexNaming;
import com.planshift.workload.QueryCategory;
import com.planshift.workload.WorkloadQuery;
import com.planshift.dataimport.CsvImportService;
import com.planshift.lock.DatabaseBusyException;
import com.planshift.lock.DatabaseLock;
import com.planshift.queryplan.ExplainService;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
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
    private DatabaseLock databaseLock;

    @Autowired
    private CsvImportService csvImportService;

    @Autowired
    private KeptIndexService keptIndexService;

    @Autowired
    private ExplainService explainService;

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
                IndexSpec.of(column), "CUSTOM_QUERY", MeasurementMode.BLOCKS);
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

        long id = experimentService.startControlAsync(List.of(QUERY), Map.of(), 5, 0.0, "iso_probe",
                MeasurementMode.BLOCKS);

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

    @Test
    void alternatingRunsTakeTurnsWithAndWithoutOneIndex() throws Exception {
        List<List<String>> seen = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            seen.add(new ArrayList<>(temporaryIndexes()));
            return invocation.callRealMethod();
        }).when(benchmarkService).executeRepeated(any(), any(), anyInt());

        long id = experimentService.startAsync(List.of(QUERY), Map.of(), 3, 0.2, "iso_probe", IndexSpec.of("a"),
                "CUSTOM_QUERY", MeasurementMode.ALTERNATING);

        assertThat(awaitFinished(id)).isEqualTo(ExperimentStatus.COMPLETED);
        List<String> with = List.of(IndexNaming.experimentName(id));
        List<String> without = List.of();
        // Rounds go before/after, after/before, before/after, as each run itself sees the database.
        assertThat(seen).containsExactly(without, with, with, without, without, with);
        Experiment experiment = experimentRepository.findById(id).orElseThrow();
        assertThat(experiment.measurement()).isEqualTo(MeasurementMode.ALTERNATING);
        ExperimentQueryResult result = experiment.queryResults().get(0);
        assertThat(result.timing().baselineSamplesMs()).hasSize(3);
        assertThat(result.timing().candidateSamplesMs()).hasSize(3);
        assertThat(result.baselinePlanJson()).contains("Seq Scan").doesNotContain("planshift_exp_");
        assertThat(result.candidatePlanJson()).contains(IndexNaming.experimentName(id));
        assertThat(indexNames()).containsExactly("idx_planshift_iso_probe_b", "my_own_idx");
    }

    @Test
    void failureWhileTheIndexIsHiddenStillRemovesIt() throws Exception {
        doAnswer(invocation -> {
            if (temporaryIndexes().isEmpty()) {
                throw new IllegalStateException("boom while the index is hidden");
            }
            return invocation.callRealMethod();
        }).when(benchmarkService).executeRepeated(any(), any(), anyInt());

        long id = experimentService.startAsync(List.of(QUERY), Map.of(), 2, 0.2, "iso_probe", IndexSpec.of("a"),
                "CUSTOM_QUERY", MeasurementMode.ALTERNATING);

        assertThat(awaitFinished(id)).isEqualTo(ExperimentStatus.FAILED);
        assertThat(indexNames()).containsExactly("idx_planshift_iso_probe_b", "my_own_idx");
    }

    @Test
    void hidingAnIndexDoesNotStopPostgresPlanningInParallel() {
        jdbc.execute("DROP TABLE IF EXISTS iso_big");
        jdbc.execute("CREATE TABLE iso_big (a INT, filler TEXT)");
        jdbc.execute("INSERT INTO iso_big SELECT g, repeat('x', 100) FROM generate_series(1, 300000) g");
        jdbc.execute("ANALYZE iso_big");
        jdbc.execute("CREATE INDEX iso_big_idx ON iso_big (a)");
        WorkloadQuery count = new WorkloadQuery("BIG", "count", QueryCategory.values()[0], "probe",
                "SELECT COUNT(*) FROM iso_big WHERE filler <> ''");

        String hidden = indexChangeService.withIndexHidden("iso_big_idx",
                () -> explainService.capture(count, Map.of()).rawJson());

        // If a transaction that had dropped an index could not use workers, every "before" run would be handicapped.
        assertThat(hidden).contains("\"Gather\"").contains("\"Workers Launched\": 2");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'iso_big_idx'",
                Integer.class)).isEqualTo(1);
        jdbc.execute("DROP TABLE iso_big");
    }

    @Test
    void anotherInstancesRunningExperimentIsLeftAloneUntilItLetsGo() throws Exception {
        long theirs = experimentRepository.insertExperiment("CUSTOM_QUERY", 0, 0, 42L, 2, 0.2, "iso_probe", "a");
        experimentRepository.markRunning(theirs);
        long mine;
        // Holding the lock is what a second instance in the middle of a run looks like from here.
        try (DatabaseLock.Held ignored = databaseLock.acquire()) {
            indexChangeService.createExperimentIndex(theirs, "iso_probe", IndexSpec.of("a"));

            recovery.recover();
            mine = start("a");
            Thread.sleep(300);

            assertThat(status(theirs)).isEqualTo(ExperimentStatus.RUNNING);
            assertThat(status(mine)).isEqualTo(ExperimentStatus.QUEUED);
            assertThat(temporaryIndexes()).containsExactly(IndexNaming.experimentName(theirs));
        }
        // Once the lock is free its row is provably orphaned, and our run cleans up before measuring.
        assertThat(awaitFinished(mine)).isEqualTo(ExperimentStatus.COMPLETED);
        assertThat(status(theirs)).isEqualTo(ExperimentStatus.FAILED);
        assertThat(indexNames()).containsExactly("idx_planshift_iso_probe_b", "my_own_idx");
    }

    @Test
    void queuedExperimentsAreOnlyGivenUpOnOnceTheirOwnerGoesQuiet() {
        long alive = experimentRepository.insertExperiment("CUSTOM_QUERY", 0, 0, 42L, 2, 0.2, "iso_probe", "a",
                MeasurementMode.BLOCKS, "another-instance");
        long quiet = experimentRepository.insertExperiment("CUSTOM_QUERY", 0, 0, 42L, 2, 0.2, "iso_probe", "a",
                MeasurementMode.BLOCKS, "dead-instance");
        jdbc.update("UPDATE experiments SET heartbeat_at = now() - interval '10 minutes' WHERE experiment_id = ?",
                quiet);

        recovery.recover();

        assertThat(status(alive)).isEqualTo(ExperimentStatus.QUEUED);
        assertThat(status(quiet)).isEqualTo(ExperimentStatus.FAILED);
        experimentRepository.failExperiment(alive);
    }

    @Test
    void importCannotReplaceATableWhileTheDatabaseIsInUse() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.csv", "text/csv",
                "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8));
        try (DatabaseLock.Held ignored = databaseLock.acquire()) {
            assertThatThrownBy(() -> csvImportService.importCsv("iso_probe", file))
                    .isInstanceOf(DatabaseBusyException.class);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iso_probe", Integer.class)).isEqualTo(2000);
        assertThat(csvImportService.importCsv("iso_probe", file).rowCount()).isEqualTo(1);
    }

    @Test
    void keepingIndexesIsOffUnlessTurnedOn() throws Exception {
        long id = start("a");
        awaitFinished(id);

        assertThatThrownBy(() -> keptIndexService.keep(id)).isInstanceOf(KeepIndexDisabledException.class);
        assertThat(indexNames()).containsExactly("idx_planshift_iso_probe_b", "my_own_idx");
    }
}
