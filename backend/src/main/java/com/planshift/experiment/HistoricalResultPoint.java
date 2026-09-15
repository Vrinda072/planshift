package com.planshift.experiment;

/** One past, completed query result -- the raw material ImpactPredictionService looks up neighbors from. */
public record HistoricalResultPoint(
        String targetTable,
        double percentageChange,
        String baselinePlanJson
) {
}
