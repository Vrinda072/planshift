package com.planshift.experiment;

import com.planshift.benchmark.BenchmarkService;
import com.planshift.benchmark.RepeatedBenchmarkResult;
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
                                   ExecutorService experimentExecutor) {
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
        return startAsync(queries, params, repetitions, thresholdFraction, table, (IndexSpec) null,
                Experiment.CONTROL_TYPE);
    }

    /** {@code index} is null only for a control run. */
    public long startAsync(List<WorkloadQuery> queries, Map<String, Object> params,
                            int repetitions, double thresholdFraction,
                            String indexTable, IndexSpec index, String experimentType) {
        if (repetitions < 1 || repetitions > MAX_REPETITIONS) {
            throw new IllegalArgumentException("Repetitions must be between 1 and " + MAX_REPETITIONS);
        }
        int datasetCustomers = schemaService.tableExists("customers") ? countRows("customers") : 0;
        int datasetOrders = schemaService.tableExists("orders") ? countRows("orders") : 0;
        long experimentId = experimentRepository.insertExperiment(
                experimentType, datasetCustomers, datasetOrders, DATASET_SEED, repetitions, thresholdFraction,
                indexTable, index == null ? null : index.columnsKey());

        try {
            executorService.execute(() -> runBody(experimentId, queries, params, repetitions, thresholdFraction,
                    indexTable, index));
        } catch (RejectedExecutionException e) {
            experimentRepository.deleteExperiment(experimentId);
            throw new QueueFullException();
        }
        return experimentId;
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

    private void runBody(long experimentId, List<WorkloadQuery> queries, Map<String, Object> params,
                          int repetitions, double thresholdFraction, String indexTable, IndexSpec index) {
        Double overallPercentageChange = null;
        try {
            experimentRepository.markRunning(experimentId);
            experimentRepository.updatePhase(experimentId, ExperimentPhase.DROPPING_INDEX);
            // Only matters if an earlier cleanup failed; if this one fails too, the baseline can't be trusted.
            indexChangeService.dropRecordedIndexes();

            experimentRepository.updatePhase(experimentId, ExperimentPhase.WARMING_UP_BASELINE);
            warmUp(queries, params);

            // The baseline is measured in two halves, one either side of the candidate. That puts both
            // configurations around the same point in time, so a machine that is steadily speeding up or slowing
            // down doesn't count for or against the index. It still creates and drops the index only once.
            experimentRepository.updatePhase(experimentId, ExperimentPhase.MEASURING_BASELINE);
            int secondHalf = repetitions / 2;
            List<RepeatedBenchmarkResult> baselineFirst = measure(queries, params, repetitions - secondHalf);
            List<ExplainCapture> baselineCaptures = queries.stream()
                    .map(q -> explainService.capture(q, params))
                    .toList();

            if (index != null) {
                experimentRepository.updatePhase(experimentId, ExperimentPhase.ADDING_INDEX);
                long started = System.nanoTime();
                String indexName = indexChangeService.createExperimentIndex(experimentId, indexTable, index);
                double buildMs = (System.nanoTime() - started) / 1_000_000.0;
                experimentRepository.saveIndexCost(experimentId, indexChangeService.sizeInBytes(indexName), buildMs);
            }

            experimentRepository.updatePhase(experimentId, ExperimentPhase.WARMING_UP_CANDIDATE);
            warmUp(queries, params);

            experimentRepository.updatePhase(experimentId, ExperimentPhase.MEASURING_CANDIDATE);
            List<RepeatedBenchmarkResult> candidateTimings = measure(queries, params, repetitions);
            List<ExplainCapture> candidateCaptures = queries.stream()
                    .map(q -> explainService.capture(q, params))
                    .toList();

            experimentRepository.updatePhase(experimentId, ExperimentPhase.REMEASURING_BASELINE);
            indexChangeService.dropRecordedIndexes();
            List<RepeatedBenchmarkResult> baselineTimings = baselineFirst;
            if (secondHalf > 0) {
                warmUp(queries, params);
                List<RepeatedBenchmarkResult> baselineSecond = measure(queries, params, secondHalf);
                baselineTimings = IntStream.range(0, queries.size())
                        .mapToObj(i -> baselineFirst.get(i).plus(baselineSecond.get(i)))
                        .toList();
            }

            experimentRepository.updatePhase(experimentId, ExperimentPhase.COMPARING_RESULTS);
            List<Double> percentageChanges = new ArrayList<>();
            for (int i = 0; i < queries.size(); i++) {
                WorkloadQuery query = queries.get(i);
                QueryComparison comparison = regressionDetector.compare(
                        query, baselineTimings.get(i), candidateTimings.get(i), thresholdFraction);
                percentageChanges.add(comparison.percentageChange());

                PlanNode baselinePlan = planParser.parse(baselineCaptures.get(i).planRoot());
                PlanNode candidatePlan = planParser.parse(candidateCaptures.get(i).planRoot());
                List<PlanDiff> diffs = planComparator.compare(baselinePlan, candidatePlan);
                String summary = planComparator.summarize(diffs);

                Timing timing = new Timing(
                        baselineTimings.get(i).executionTimesMs(), candidateTimings.get(i).executionTimesMs(),
                        comparison.pValue(), comparison.evidence(),
                        planStats(baselineCaptures.get(i)), planStats(candidateCaptures.get(i)),
                        benchmarkService.timingSource());
                ExperimentQueryResult result = new ExperimentQueryResult(
                        0, experimentId, comparison.queryId(), comparison.queryName(),
                        comparison.baselineMedianMs(), comparison.candidateMedianMs(),
                        comparison.absoluteChangeMs(), comparison.percentageChange(), comparison.status(),
                        toJson(baselinePlan), toJson(candidatePlan), summary, timing);
                experimentRepository.insertQueryResult(experimentId, result);
            }

            overallPercentageChange = median(percentageChanges);
        } catch (RuntimeException e) {
            log.error("Experiment {} failed", experimentId, e);
        } finally {
            // Clean up before publishing a final status, so a finished experiment has already removed its index.
            removeExperimentIndex(experimentId);
            if (overallPercentageChange != null) {
                experimentRepository.completeExperiment(experimentId, overallPercentageChange);
            } else {
                experimentRepository.failExperiment(experimentId);
            }
        }
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
