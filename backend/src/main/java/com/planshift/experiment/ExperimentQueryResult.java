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
        String planDiffSummary,
        Timing timing,
        /** The query as it was run, with its values written in, for people to read. Null on older results. */
        String querySql
) {
}
