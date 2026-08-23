package com.planshift.experiment;

public record ExperimentQueryResult(
        long id,
        long experimentId,
        String queryId,
        String queryName,
        double baselineMedianMs,
        double candidateMedianMs,
        double absoluteChangeMs,
        double percentageChange,
        RegressionStatus status,
        String baselinePlanJson,
        String candidatePlanJson,
        String planDiffSummary
) {
}
