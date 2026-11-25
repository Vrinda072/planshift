package com.planshift.experiment;

import com.planshift.benchmark.TimingSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ExperimentRepository {

    private final JdbcTemplate jdbc;

    public ExperimentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insertExperiment(String experimentType, int datasetCustomers, int datasetOrders,
                                  long datasetSeed, int repetitions, double thresholdFraction,
                                  String targetTable, String targetColumn) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO experiments (experiment_type, dataset_customers, dataset_orders, dataset_seed, "
                            + "repetitions, threshold_fraction, status, current_phase, target_table, target_column) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"experiment_id"});
            ps.setString(1, experimentType);
            ps.setInt(2, datasetCustomers);
            ps.setInt(3, datasetOrders);
            ps.setLong(4, datasetSeed);
            ps.setInt(5, repetitions);
            ps.setDouble(6, thresholdFraction);
            ps.setString(7, ExperimentStatus.QUEUED.name());
            ps.setString(8, ExperimentPhase.PENDING.name());
            ps.setString(9, targetTable);
            ps.setString(10, targetColumn);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void markRunning(long experimentId) {
        jdbc.update("UPDATE experiments SET status = ? WHERE experiment_id = ?",
                ExperimentStatus.RUNNING.name(), experimentId);
    }

    public void deleteExperiment(long experimentId) {
        jdbc.update("DELETE FROM experiments WHERE experiment_id = ?", experimentId);
    }

    /** Marks everything still queued or running as failed. Only safe when no worker can be running, i.e. at startup. */
    public int failUnfinished() {
        return jdbc.update("UPDATE experiments SET status = ?, current_phase = ? WHERE status IN (?, ?)",
                ExperimentStatus.FAILED.name(), ExperimentPhase.DONE.name(),
                ExperimentStatus.QUEUED.name(), ExperimentStatus.RUNNING.name());
    }

    public void updatePhase(long experimentId, ExperimentPhase phase) {
        jdbc.update("UPDATE experiments SET current_phase = ? WHERE experiment_id = ?",
                phase.name(), experimentId);
    }

    public void insertQueryResult(long experimentId, ExperimentQueryResult r) {
        Timing t = r.timing();
        jdbc.update(
                "INSERT INTO experiment_query_results (experiment_id, query_id, query_name, baseline_median_ms, "
                        + "candidate_median_ms, absolute_change_ms, percentage_change, status, baseline_plan_json, "
                        + "candidate_plan_json, plan_diff_summary, baseline_samples_ms, candidate_samples_ms, p_value, "
                        + "timing_evidence, baseline_db_ms, baseline_hit_blocks, baseline_read_blocks, candidate_db_ms, "
                        + "candidate_hit_blocks, candidate_read_blocks, timing_source) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                experimentId, r.queryId(), r.queryName(), r.baselineMedianMs(), r.candidateMedianMs(),
                r.absoluteChangeMs(), r.percentageChange(), r.status().name(),
                r.baselinePlanJson(), r.candidatePlanJson(), r.planDiffSummary(),
                joinSamples(t.baselineSamplesMs()), joinSamples(t.candidateSamplesMs()), t.pValue(),
                t.evidence().name(),
                t.baselinePlanStats().executionTimeMs(), t.baselinePlanStats().sharedHitBlocks(),
                t.baselinePlanStats().sharedReadBlocks(),
                t.candidatePlanStats().executionTimeMs(), t.candidatePlanStats().sharedHitBlocks(),
                t.candidatePlanStats().sharedReadBlocks(), t.source().name());
    }

    private static String joinSamples(List<Double> samples) {
        return samples.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static List<Double> splitSamples(String joined) {
        return Arrays.stream(joined.split(",")).map(Double::valueOf).toList();
    }

    private Timing mapTiming(java.sql.ResultSet rs) throws java.sql.SQLException {
        String evidence = rs.getString("timing_evidence");
        if (evidence == null) {
            return null;
        }
        return new Timing(
                splitSamples(rs.getString("baseline_samples_ms")),
                splitSamples(rs.getString("candidate_samples_ms")),
                rs.getDouble("p_value"),
                TimingEvidence.valueOf(evidence),
                new PlanStats(rs.getDouble("baseline_db_ms"), rs.getLong("baseline_hit_blocks"),
                        rs.getLong("baseline_read_blocks")),
                new PlanStats(rs.getDouble("candidate_db_ms"), rs.getLong("candidate_hit_blocks"),
                        rs.getLong("candidate_read_blocks")),
                TimingSource.valueOf(rs.getString("timing_source")));
    }

    public void completeExperiment(long experimentId, double overallPercentageChange) {
        jdbc.update("UPDATE experiments SET status = ?, current_phase = ?, overall_percentage_change = ? WHERE experiment_id = ?",
                ExperimentStatus.COMPLETED.name(), ExperimentPhase.DONE.name(), overallPercentageChange, experimentId);
    }

    public void failExperiment(long experimentId) {
        jdbc.update("UPDATE experiments SET status = ?, current_phase = ? WHERE experiment_id = ?",
                ExperimentStatus.FAILED.name(), ExperimentPhase.DONE.name(), experimentId);
    }

    public List<Experiment> findAllSummaries() {
        return jdbc.query(
                "SELECT * FROM experiments ORDER BY created_at DESC",
                (rs, rowNum) -> mapExperiment(rs, List.of()));
    }

    public Optional<Experiment> findById(long experimentId) {
        List<Experiment> matches = jdbc.query(
                "SELECT * FROM experiments WHERE experiment_id = ?",
                (rs, rowNum) -> mapExperiment(rs, findQueryResults(experimentId)),
                experimentId);
        return matches.stream().findFirst();
    }

    private List<ExperimentQueryResult> findQueryResults(long experimentId) {
        return jdbc.query(
                "SELECT * FROM experiment_query_results WHERE experiment_id = ? ORDER BY id",
                this::mapQueryResult,
                experimentId);
    }

    public void saveQueryFeatures(long experimentId, String access, String operator, double selectivity,
                                   boolean coversOrder) {
        jdbc.update("UPDATE experiments SET query_access = ?, query_operator = ?, query_selectivity = ?, "
                + "query_covers_order = ? WHERE experiment_id = ?",
                access, operator, selectivity, coversOrder, experimentId);
    }

    public void saveIndexCost(long experimentId, long sizeBytes, double buildMs) {
        jdbc.update("UPDATE experiments SET index_size_bytes = ?, index_build_ms = ? WHERE experiment_id = ?",
                sizeBytes, buildMs, experimentId);
    }

    /** Completed runs that used an index the same way for the same kind of filter. */
    public List<HistoricalResultPoint> findComparableChanges(String access, String operator, boolean coversOrder) {
        return jdbc.query(
                "SELECT e.query_selectivity AS selectivity, eqr.percentage_change AS percentage_change "
                        + "FROM experiment_query_results eqr "
                        + "JOIN experiments e ON eqr.experiment_id = e.experiment_id "
                        + "WHERE e.status = 'COMPLETED' AND e.query_access = ? AND e.query_operator = ? "
                        + "AND e.query_covers_order = ? AND e.query_selectivity IS NOT NULL",
                (rs, rowNum) -> new HistoricalResultPoint(
                        rs.getDouble("selectivity"), rs.getDouble("percentage_change")),
                access, operator, coversOrder);
    }

    public List<ExperimentQueryResult> findAllRegressions() {
        return jdbc.query(
                "SELECT eqr.* FROM experiment_query_results eqr "
                        + "JOIN experiments e ON eqr.experiment_id = e.experiment_id "
                        + "WHERE eqr.status = 'REGRESSED' AND e.experiment_type <> '" + Experiment.CONTROL_TYPE + "' "
                        + "ORDER BY e.created_at DESC",
                this::mapQueryResult);
    }

    private ExperimentQueryResult mapQueryResult(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ExperimentQueryResult(
                rs.getLong("id"),
                rs.getLong("experiment_id"),
                rs.getString("query_id"),
                rs.getString("query_name"),
                rs.getDouble("baseline_median_ms"),
                rs.getDouble("candidate_median_ms"),
                rs.getDouble("absolute_change_ms"),
                rs.getDouble("percentage_change"),
                RegressionStatus.valueOf(rs.getString("status")),
                rs.getString("baseline_plan_json"),
                rs.getString("candidate_plan_json"),
                rs.getString("plan_diff_summary"),
                mapTiming(rs)
        );
    }

    private Experiment mapExperiment(java.sql.ResultSet rs, List<ExperimentQueryResult> queryResults) throws java.sql.SQLException {
        Double overallChange = rs.getObject("overall_percentage_change") != null
                ? rs.getDouble("overall_percentage_change") : null;
        String phase = rs.getString("current_phase");
        return new Experiment(
                rs.getLong("experiment_id"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getString("experiment_type"),
                rs.getInt("dataset_customers"),
                rs.getInt("dataset_orders"),
                rs.getLong("dataset_seed"),
                rs.getString("target_table"),
                rs.getString("target_column"),
                rs.getInt("repetitions"),
                rs.getDouble("threshold_fraction"),
                rs.getObject("index_size_bytes", Long.class),
                rs.getObject("index_build_ms", Double.class),
                ExperimentStatus.valueOf(rs.getString("status")),
                phase != null ? ExperimentPhase.valueOf(phase) : null,
                overallChange,
                queryResults
        );
    }
}
