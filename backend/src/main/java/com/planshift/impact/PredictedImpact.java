package com.planshift.impact;

/**
 * A pre-experiment estimate of what adding an index would do to a query, without running the measurement.
 *
 * method is "planner", "historical" (the median of comparable past experiments), "structure" (the index can't serve the query)
 * or "heuristic" (a rule of thumb from the planner's row estimate). confidence is "moderate" only when there is
 * comparable history; everything else is "low".
 */
public record PredictedImpact(
        double predictedPercentageChange,
        String method,
        String confidence,
        int sampleSize,
        double selectivity,
        String currentScanType,
        String note
) {

    PredictedImpact withNote(String replacement) {
        return new PredictedImpact(predictedPercentageChange, method, confidence, sampleSize, selectivity,
                currentScanType, replacement);
    }
}
