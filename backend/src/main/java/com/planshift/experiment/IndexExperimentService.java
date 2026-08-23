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

/**
 * Orchestrates an index-toggle experiment: runs a workload with an index on
 * a chosen table.column absent (baseline), then present (candidate),
 * capturing timings and execution plans, and persists the full result.
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
     * Creates the experiment row immediately (status RUNNING, phase PENDING)
     * and submits the real work to run in the background. Returns the new
     * experiment id right away.
     */
    public long startAsync(List<WorkloadQuery> queries, Map<String, Object> params,
                            int repetitions, double thresholdFraction,
                            String indexTable, String indexColumn, String experimentType) {
        int datasetCustomers = schemaService.tableExists("customers") ? countRows("customers") : 0;
        int datasetOrders = schemaService.tableExists("orders") ? countRows("orders") : 0;
        long experimentId = experimentRepository.insertExperiment(
                experimentType, datasetCustomers, datasetOrders, DATASET_SEED, repetitions, thresholdFraction,
                indexTable, indexColumn);

        executorService.submit(() -> runBody(experimentId, queries, params, repetitions, thresholdFraction,
                indexTable, indexColumn));
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
            if (experiment.status() != ExperimentStatus.RUNNING) {
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
                          int repetitions, double thresholdFraction, String indexTable, String indexColumn) {
        try {
            experimentRepository.updatePhase(experimentId, ExperimentPhase.DROPPING_INDEX);
            indexChangeService.dropIndex(indexTable, indexColumn);

            experimentRepository.updatePhase(experimentId, ExperimentPhase.WARMING_UP_BASELINE);
            warmUp(queries, params);

            experimentRepository.updatePhase(experimentId, ExperimentPhase.MEASURING_BASELINE);
            List<RepeatedBenchmarkResult> baselineTimings = queries.stream()
                    .map(q -> benchmarkService.executeRepeated(q, params, repetitions))
                    .toList();
            List<PlanNode> baselinePlans = queries.stream()
                    .map(q -> capturePlan(q, params))
                    .toList();

            experimentRepository.updatePhase(experimentId, ExperimentPhase.ADDING_INDEX);
            indexChangeService.addIndex(indexTable, indexColumn);

            experimentRepository.updatePhase(experimentId, ExperimentPhase.WARMING_UP_CANDIDATE);
            warmUp(queries, params);

            experimentRepository.updatePhase(experimentId, ExperimentPhase.MEASURING_CANDIDATE);
            List<RepeatedBenchmarkResult> candidateTimings = queries.stream()
                    .map(q -> benchmarkService.executeRepeated(q, params, repetitions))
                    .toList();
            List<PlanNode> candidatePlans = queries.stream()
                    .map(q -> capturePlan(q, params))
                    .toList();

            experimentRepository.updatePhase(experimentId, ExperimentPhase.COMPARING_RESULTS);
            List<Double> percentageChanges = new ArrayList<>();
            for (int i = 0; i < queries.size(); i++) {
                WorkloadQuery query = queries.get(i);
                QueryComparison comparison = regressionDetector.compare(
                        query, baselineTimings.get(i), candidateTimings.get(i), thresholdFraction);
                percentageChanges.add(comparison.percentageChange());

                List<PlanDiff> diffs = planComparator.compare(baselinePlans.get(i), candidatePlans.get(i));
                String summary = planComparator.summarize(diffs);

                ExperimentQueryResult result = new ExperimentQueryResult(
                        0, experimentId, comparison.queryId(), comparison.queryName(),
                        comparison.baselineMedianMs(), comparison.candidateMedianMs(),
                        comparison.absoluteChangeMs(), comparison.percentageChange(), comparison.status(),
                        toJson(baselinePlans.get(i)), toJson(candidatePlans.get(i)), summary);
                experimentRepository.insertQueryResult(experimentId, result);
            }

            double overallPercentageChange = median(percentageChanges);
            experimentRepository.completeExperiment(experimentId, overallPercentageChange);
        } catch (RuntimeException e) {
            log.error("Experiment {} failed", experimentId, e);
            experimentRepository.failExperiment(experimentId);
        }
    }

    private PlanNode capturePlan(WorkloadQuery query, Map<String, Object> params) {
        ExplainCapture capture = explainService.capture(query, params);
        return planParser.parse(capture.planRoot());
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
