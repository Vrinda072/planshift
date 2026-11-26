package com.planshift.experiment;

import com.planshift.benchmark.BenchmarkService;
import com.planshift.benchmark.RepeatedBenchmarkResult;
import com.planshift.lock.DatabaseLock;
import com.planshift.queryplan.ExplainCapture;
import com.planshift.queryplan.ExplainService;
import com.planshift.queryplan.PlanComparator;
import com.planshift.queryplan.PlanDiff;
import com.planshift.queryplan.PlanNode;
import com.planshift.queryplan.PlanParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planshift.schema.SchemaService;
import com.planshift.workload.WorkloadCatalog;
import com.planshift.workload.WorkloadQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Orchestrates an index experiment: runs a workload without a new index
 * (baseline), creates a temporary one on the chosen columns, runs it again
 * (candidate), removes it, and persists timings, plans and the comparison.
 * Indexes that already exist are left alone and are part of both sides.
 *
 * Generic over table/column and query list, so this same orchestrator
 * backs both the built-in orders.customer_id experiment and arbitrary
 * query-builder experiments against any real table.
 *
 * The actual work runs on a background thread ({@link #startAsync}) so the
 * REST API can return immediately with a RUNNING experiment the client can
 * poll -- a real progress signal, not a simulated one, since the persisted
 * current_phase column is updated at each genuine step boundary.
 */
@Service
public class IndexExperimentService {

    private static final Logger log = LoggerFactory.getLogger(IndexExperimentService.class);
    private static final long DATASET_SEED = 42L;
    static final int MAX_REPETITIONS = 30;

    private final IndexChangeService indexChangeService;
    private final BenchmarkService benchmarkService;
    private final RegressionDetector regressionDetector;
    private final ExplainService explainService;
    private final PlanParser planParser;
    private final PlanComparator planComparator;
    private final ExperimentRepository experimentRepository;
    private final SchemaService schemaService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final DatabaseLock databaseLock;
    private final ExperimentRecovery recovery;
    private final ExperimentOwner owner;

    public IndexExperimentService(IndexChangeService indexChangeService,
                                   BenchmarkService benchmarkService,
                                   RegressionDetector regressionDetector,
                                   ExplainService explainService,
                                   PlanParser planParser,
                                   PlanComparator planComparator,
                                   ExperimentRepository experimentRepository,
                                   SchemaService schemaService,
                                   JdbcTemplate jdbc,
                                   ObjectMapper objectMapper,
                                   ExecutorService experimentExecutor,
                                   DatabaseLock databaseLock,
                                   ExperimentRecovery recovery,
                                   ExperimentOwner owner) {
        this.indexChangeService = indexChangeService;
        this.benchmarkService = benchmarkService;
        this.regressionDetector = regressionDetector;
        this.explainService = explainService;
        this.planParser = planParser;
        this.planComparator = planComparator;
        this.experimentRepository = experimentRepository;
        this.schemaService = schemaService;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.executorService = experimentExecutor;
        this.databaseLock = databaseLock;
        this.recovery = recovery;
        this.owner = owner;
    }

    /**
     * Creates the experiment row immediately (status QUEUED, phase PENDING)
     * and queues the real work for the background worker. Returns the new
     * experiment id right away.
     */
    public long startAsync(List<WorkloadQuery> queries, Map<String, Object> params,
                            int repetitions, double thresholdFraction,
                            String indexTable, String indexColumn, String experimentType) {
        return startAsync(queries, params, repetitions, thresholdFraction, indexTable,
                IndexSpec.of(indexColumn), experimentType);
    }

    /** Starts an A/A control: the whole procedure with no change between the two sides, to show normal variation. */
    public long startControlAsync(List<WorkloadQuery> queries, Map<String, Object> params,
                                   int repetitions, double thresholdFraction, String table) {
        return startControlAsync(queries, params, repetitions, thresholdFraction, table, MeasurementMode.DEFAULT);
    }

    public long startControlAsync(List<WorkloadQuery> queries, Map<String, Object> params, int repetitions,
                                   double thresholdFraction, String table, MeasurementMode measurement) {
        return startAsync(queries, params, repetitions, thresholdFraction, table, (IndexSpec) null,
                Experiment.CONTROL_TYPE, measurement);
    }

    public long startAsync(List<WorkloadQuery> queries, Map<String, Object> params,
                            int repetitions, double thresholdFraction,
                            String indexTable, IndexSpec index, String experimentType) {
        return startAsync(queries, params, repetitions, thresholdFraction, indexTable, index, experimentType,
                MeasurementMode.DEFAULT);
    }

    /** {@code index} is null only for a control run. */
    public long startAsync(List<WorkloadQuery> queries, Map<String, Object> params,
                            int repetitions, double thresholdFraction,
                            String indexTable, IndexSpec index, String experimentType,
                            MeasurementMode measurement) {
        if (repetitions < 1 || repetitions > MAX_REPETITIONS) {
            throw new IllegalArgumentException("Repetitions must be between 1 and " + MAX_REPETITIONS);
        }
        int datasetCustomers = schemaService.tableExists("customers") ? countRows("customers") : 0;
        int datasetOrders = schemaService.tableExists("orders") ? countRows("orders") : 0;
        long experimentId = experimentRepository.insertExperiment(
                experimentType, datasetCustomers, datasetOrders, DATASET_SEED, repetitions, thresholdFraction,
                indexTable, index == null ? null : index.columnsKey(), measurement, owner.id());

        Job job = new Job(experimentId, queries, params, repetitions, thresholdFraction, indexTable, index,
                measurement);
        owner.jobAccepted();
        try {
            executorService.execute(() -> run(job));
        } catch (RejectedExecutionException e) {
            owner.jobFinished();
            experimentRepository.deleteExperiment(experimentId);
            throw new QueueFullException();
        }
        return experimentId;
    }

    private record Job(long id, List<WorkloadQuery> queries, Map<String, Object> params, int repetitions,
                       double thresholdFraction, String table, IndexSpec index, MeasurementMode measurement) {
    }

    /** Everything one side produced, in the order of the workload's queries. */
    private record Measurements(List<RepeatedBenchmarkResult> baseline, List<ExplainCapture> baselineCaptures,
                                List<RepeatedBenchmarkResult> candidate, List<ExplainCapture> candidateCaptures) {
    }

    /** Starts the default full-workload experiment and blocks until it finishes. Used by tests and the CLI runner. */
    public long runDefault() {
        long experimentId = startAsync(WorkloadCatalog.all(), WorkloadCatalog.defaultParams(), 5,
                RegressionDetector.DEFAULT_THRESHOLD_FRACTION, "orders", "customer_id", "INDEX_ADD");
        awaitCompletion(experimentId);
        return experimentId;
    }

    private void awaitCompletion(long experimentId) {
        while (true) {
            Experiment experiment = experimentRepository.findById(experimentId).orElseThrow();
            if (experiment.status() == ExperimentStatus.COMPLETED || experiment.status() == ExperimentStatus.FAILED) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void run(Job job) {
        try (DatabaseLock.Held ignored = databaseLock.acquire()) {
            runHoldingLock(job);
        } catch (RuntimeException e) {
            log.error("Experiment {} could not run", job.id(), e);
            experimentRepository.failExperiment(job.id());
        } finally {
            owner.jobFinished();
        }
    }

    private void runHoldingLock(Job job) {
        Double overallPercentageChange = null;
        boolean started = false;
        try {
            // Clears up anything an interrupted run left behind, including an earlier cleanup that failed. If this
            // fails, the baseline can't be trusted.
            recovery.recoverHoldingLock();
            started = experimentRepository.markRunning(job.id());
            if (!started) {
                log.warn("Experiment {} is no longer queued; not running it", job.id());
                return;
            }
            experimentRepository.updatePhase(job.id(), ExperimentPhase.DROPPING_INDEX);

            Measurements measured = job.measurement() == MeasurementMode.ALTERNATING
                    ? measureAlternating(job) : measureInBlocks(job);
            overallPercentageChange = compareAndSave(job, measured);
        } catch (RuntimeException e) {
            log.error("Experiment {} failed", job.id(), e);
            started = true;
        } finally {
            if (started) {
                // Clean up before publishing a final status, so a finished experiment has already removed its index.
                removeExperimentIndex(job.id());
                if (overallPercentageChange != null) {
                    experimentRepository.completeExperiment(job.id(), overallPercentageChange);
                } else {
                    experimentRepository.failExperiment(job.id());
                }
            }
        }
    }

    /**
     * The baseline is measured in two halves, one either side of the candidate. That puts both configurations
     * around the same point in time, so a machine that is steadily speeding up or slowing down doesn't count for or
     * against the index. It still creates and drops the index only once.
     */
    private Measurements measureInBlocks(Job job) {
        experimentRepository.updatePhase(job.id(), ExperimentPhase.WARMING_UP_BASELINE);
        warmUp(job.queries(), job.params());

        experimentRepository.updatePhase(job.id(), ExperimentPhase.MEASURING_BASELINE);
        int secondHalf = job.repetitions() / 2;
        List<RepeatedBenchmarkResult> baselineFirst =
                measure(job.queries(), job.params(), job.repetitions() - secondHalf);
        List<ExplainCapture> baselineCaptures = capture(job);

        if (job.index() != null) {
            createIndex(job);
        }

        experimentRepository.updatePhase(job.id(), ExperimentPhase.WARMING_UP_CANDIDATE);
        warmUp(job.queries(), job.params());

        experimentRepository.updatePhase(job.id(), ExperimentPhase.MEASURING_CANDIDATE);
        List<RepeatedBenchmarkResult> candidate = measure(job.queries(), job.params(), job.repetitions());
        List<ExplainCapture> candidateCaptures = capture(job);

        experimentRepository.updatePhase(job.id(), ExperimentPhase.REMEASURING_BASELINE);
        indexChangeService.dropRecordedIndexes();
        List<RepeatedBenchmarkResult> baseline = baselineFirst;
        if (secondHalf > 0) {
            warmUp(job.queries(), job.params());
            baseline = append(baselineFirst, measure(job.queries(), job.params(), secondHalf));
        }
        return new Measurements(baseline, baselineCaptures, candidate, candidateCaptures);
    }

    /**
     * The two sides take turns, one timed run each, swapping which goes first every round. Both run inside a
     * rolled-back transaction after an untimed run of the same query, so the only difference between them is
     * whether the index is hidden.
     */
    private Measurements measureAlternating(Job job) {
        String indexName = job.index() == null ? null : createIndex(job);

        experimentRepository.updatePhase(job.id(), ExperimentPhase.WARMING_UP_CANDIDATE);
        warmUp(job.queries(), job.params());

        experimentRepository.updatePhase(job.id(), ExperimentPhase.ALTERNATING_RUNS);
        List<RepeatedBenchmarkResult> baseline = null;
        List<RepeatedBenchmarkResult> candidate = null;
        for (int round = 0; round < job.repetitions(); round++) {
            boolean baselineFirst = round % 2 == 0;
            List<RepeatedBenchmarkResult> first = oneRun(job, indexName, baselineFirst);
            List<RepeatedBenchmarkResult> second = oneRun(job, indexName, !baselineFirst);
            List<RepeatedBenchmarkResult> without = baselineFirst ? first : second;
            List<RepeatedBenchmarkResult> with = baselineFirst ? second : first;
            baseline = baseline == null ? without : append(baseline, without);
            candidate = candidate == null ? with : append(candidate, with);
        }

        List<ExplainCapture> baselineCaptures = job.queries().stream()
                .map(q -> onSide(indexName, true, () -> explainService.capture(q, job.params())))
                .toList();
        List<ExplainCapture> candidateCaptures = job.queries().stream()
                .map(q -> onSide(indexName, false, () -> explainService.capture(q, job.params())))
                .toList();
        return new Measurements(baseline, baselineCaptures, candidate, candidateCaptures);
    }

    private List<RepeatedBenchmarkResult> oneRun(Job job, String indexName, boolean withoutIndex) {
        return job.queries().stream()
                .map(q -> onSide(indexName, withoutIndex, () -> {
                    benchmarkService.executeTimed(q, job.params());
                    return benchmarkService.executeRepeated(q, job.params(), 1);
                }))
                .toList();
    }

    private <T> T onSide(String indexName, boolean withoutIndex, Supplier<T> work) {
        return withoutIndex && indexName != null
                ? indexChangeService.withIndexHidden(indexName, work)
                : indexChangeService.rolledBack(work);
    }

    private String createIndex(Job job) {
        experimentRepository.updatePhase(job.id(), ExperimentPhase.ADDING_INDEX);
        long started = System.nanoTime();
        String indexName = indexChangeService.createExperimentIndex(job.id(), job.table(), job.index());
        double buildMs = (System.nanoTime() - started) / 1_000_000.0;
        experimentRepository.saveIndexCost(job.id(), indexChangeService.sizeInBytes(indexName), buildMs);
        return indexName;
    }

    private List<ExplainCapture> capture(Job job) {
        return job.queries().stream()
                .map(q -> explainService.capture(q, job.params()))
                .toList();
    }

    private static List<RepeatedBenchmarkResult> append(List<RepeatedBenchmarkResult> first,
                                                        List<RepeatedBenchmarkResult> second) {
        return IntStream.range(0, first.size())
                .mapToObj(i -> first.get(i).plus(second.get(i)))
                .toList();
    }

    /** Returns the median change across the workload. */
    private double compareAndSave(Job job, Measurements measured) {
        experimentRepository.updatePhase(job.id(), ExperimentPhase.COMPARING_RESULTS);
        List<Double> percentageChanges = new ArrayList<>();
        for (int i = 0; i < job.queries().size(); i++) {
            WorkloadQuery query = job.queries().get(i);
            QueryComparison comparison = regressionDetector.compare(
                    query, measured.baseline().get(i), measured.candidate().get(i), job.thresholdFraction());
            percentageChanges.add(comparison.percentageChange());

            PlanNode baselinePlan = planParser.parse(measured.baselineCaptures().get(i).planRoot());
            PlanNode candidatePlan = planParser.parse(measured.candidateCaptures().get(i).planRoot());
            List<PlanDiff> diffs = planComparator.compare(baselinePlan, candidatePlan);
            String summary = planComparator.summarize(diffs);

            Timing timing = new Timing(
                    measured.baseline().get(i).executionTimesMs(), measured.candidate().get(i).executionTimesMs(),
                    comparison.pValue(), comparison.evidence(),
                    planStats(measured.baselineCaptures().get(i)), planStats(measured.candidateCaptures().get(i)),
                    benchmarkService.timingSource());
            ExperimentQueryResult result = new ExperimentQueryResult(
                    0, job.id(), comparison.queryId(), comparison.queryName(),
                    comparison.baselineMedianMs(), comparison.candidateMedianMs(),
                    comparison.absoluteChangeMs(), comparison.percentageChange(), comparison.status(),
                    toJson(baselinePlan), toJson(candidatePlan), summary, timing,
                    DisplaySql.of(query.sql(), job.params()));
            experimentRepository.insertQueryResult(job.id(), result);
        }
        return median(percentageChanges);
    }

    private void removeExperimentIndex(long experimentId) {
        try {
            indexChangeService.dropRecordedIndexes();
        } catch (RuntimeException e) {
            // Ownership stays recorded, so the next run (or the next startup) retries.
            log.error("Could not remove the index of experiment {}", experimentId, e);
        }
    }

    private List<RepeatedBenchmarkResult> measure(List<WorkloadQuery> queries, Map<String, Object> params, int runs) {
        return queries.stream()
                .map(q -> benchmarkService.executeRepeated(q, params, runs))
                .toList();
    }

    private PlanStats planStats(ExplainCapture capture) {
        return new PlanStats(capture.executionTimeMs(),
                capture.planRoot().path("Shared Hit Blocks").asLong(),
                capture.planRoot().path("Shared Read Blocks").asLong());
    }

    private String toJson(PlanNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize plan node to JSON", e);
        }
    }

    private int countRows(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * Runs the workload once, untimed, before measurement begins. Without this,
     * whichever phase (baseline or candidate) runs second benefits from a
     * warmer Postgres shared_buffers / OS page cache purely from the first
     * phase's activity -- an ordering bias unrelated to the index itself.
     * Warming up both phases independently removes that confound.
     */
    private void warmUp(List<WorkloadQuery> queries, Map<String, Object> params) {
        queries.forEach(q -> benchmarkService.executeTimed(q, params));
    }

    private double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int n = sorted.size();
        int mid = n / 2;
        return (n % 2 == 0) ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0 : sorted.get(mid);
    }
}
