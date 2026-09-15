package com.planshift.impact;

/**
 * A pre-experiment estimate of what adding an index would do to a query,
 * without actually running the slow real before/after measurement.
 *
 * method is either "historical" (averaged from past experiments with a
 * similar selectivity) or "heuristic" (a rule-of-thumb based purely on the
 * planner's own row estimate, used when there isn't enough history yet).
 */
public record PredictedImpact(
        double predictedPercentageChange,
        String method,
        int sampleSize,
        double selectivity,
        String currentScanType,
        String note
) {
}
