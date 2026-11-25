package com.planshift.benchmark;

import java.util.ArrayList;
import java.util.List;

public record RepeatedBenchmarkResult(
        String queryId,
        List<Double> executionTimesMs,
        double medianMs,
        int rowsReturned
) {

    /** Pools two batches of runs of the same query. */
    public RepeatedBenchmarkResult plus(RepeatedBenchmarkResult other) {
        List<Double> times = new ArrayList<>(executionTimesMs);
        times.addAll(other.executionTimesMs);
        return new RepeatedBenchmarkResult(queryId, List.copyOf(times), median(times), other.rowsReturned);
    }

    public static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int n = sorted.size();
        int mid = n / 2;
        return (n % 2 == 0) ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0 : sorted.get(mid);
    }
}
