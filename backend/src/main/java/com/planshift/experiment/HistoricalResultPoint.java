package com.planshift.experiment;

/** One past measured change, with the selectivity its filter had at the time. */
public record HistoricalResultPoint(
        double selectivity,
        double percentageChange
) {
}
