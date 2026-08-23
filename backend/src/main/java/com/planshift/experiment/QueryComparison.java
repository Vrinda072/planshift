package com.planshift.experiment;

public record QueryComparison(
        String queryId,
        String queryName,
        double baselineMedianMs,
        double candidateMedianMs,
        double absoluteChangeMs,
        double percentageChange,
        RegressionStatus status
) {
}
