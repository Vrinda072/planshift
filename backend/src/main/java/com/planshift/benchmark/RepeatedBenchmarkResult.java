package com.planshift.benchmark;

import java.util.List;

public record RepeatedBenchmarkResult(
        String queryId,
        List<Double> executionTimesMs,
        double medianMs,
        int rowsReturned
) {
}
