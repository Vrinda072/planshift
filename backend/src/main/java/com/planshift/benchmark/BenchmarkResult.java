package com.planshift.benchmark;

public record BenchmarkResult(
        String queryId,
        double executionTimeMs,
        int rowsReturned
) {
}
