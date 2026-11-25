package com.planshift.experiment;

import java.time.LocalDateTime;
import java.util.List;

public record Experiment(
        long experimentId,
        LocalDateTime createdAt,
        String experimentType,
        int datasetCustomers,
        int datasetOrders,
        long datasetSeed,
        String targetTable,
        String targetColumn,
        int repetitions,
        double thresholdFraction,
        ExperimentStatus status,
        ExperimentPhase currentPhase,
        Double overallPercentageChange,
        List<ExperimentQueryResult> queryResults
) {

    /** An A/A run. It measures noise, so it is left out of regressions and prediction history. */
    public static final String CONTROL_TYPE = "AA_CONTROL";
}
