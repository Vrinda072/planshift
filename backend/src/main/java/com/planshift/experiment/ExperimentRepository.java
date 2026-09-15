package com.planshift.experiment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

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
            ps.setString(7, ExperimentStatus.RUNNING.name());
            ps.setString(8, ExperimentPhase.PENDING.name());
            ps.setString(9, targetTable);
            ps.setString(10, targetColumn);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void updatePhase(long experimentId, ExperimentPhase phase) {
        jdbc.update("UPDATE experiments SET current_phase = ? WHERE experiment_id = ?",
                phase.name(), experimentId);
    }

    public void insertQueryResult(long experimentId, ExperimentQueryResult r) {
        jdbc.update(
                "INSERT INTO experiment_query_results (experiment_id, query_id, query_name, baseline_median_ms, "
                        + "candidate_median_ms, absolute_change_ms, percentage_change, status, baseline_plan_json, "
                        + "candidate_plan_json, plan_diff_summary) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                experimentId, r.queryId(), r.queryName(), r.baselineMedianMs(), r.candidateMedianMs(),
                r.absoluteChangeMs(), r.percentageChange(), r.status().name(),
                r.baselinePlanJson(), r.candidatePlanJson(), r.planDiffSummary());
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

    /** Completed results with a stored baseline plan -- the pool ImpactPredictionService searches for similar past cases. */
    public List<HistoricalResultPoint> findCompletedResultsWithPlans() {
        return jdbc.query(
                "SELECT e.target_table AS target_table, eqr.percentage_change AS percentage_change, "
                        + "eqr.baseline_plan_json AS baseline_plan_json "
                        + "FROM experiment_query_results eqr "
                        + "JOIN experiments e ON eqr.experiment_id = e.experiment_id "
                        + "WHERE e.status = 'COMPLETED' AND eqr.baseline_plan_json IS NOT NULL AND e.target_table IS NOT NULL",
                (rs, rowNum) -> new HistoricalResultPoint(
                        rs.getString("target_table"),
                        rs.getDouble("percentage_change"),
                        rs.getString("baseline_plan_json")));
    }

    public List<ExperimentQueryResult> findAllRegressions() {
        return jdbc.query(
                "SELECT eqr.* FROM experiment_query_results eqr "
                        + "JOIN experiments e ON eqr.experiment_id = e.experiment_id "
                        + "WHERE eqr.status = 'REGRESSED' "
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
                rs.getString("plan_diff_summary")
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
                ExperimentStatus.valueOf(rs.getString("status")),
                phase != null ? ExperimentPhase.valueOf(phase) : null,
                overallChange,
                queryResults
        );
    }
}
