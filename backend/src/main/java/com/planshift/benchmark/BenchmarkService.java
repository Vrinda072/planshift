package com.planshift.benchmark;

import com.planshift.workload.WorkloadQuery;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes a predefined, read-only workload query against Postgres and times
 * it from the caller's side (wall-clock: JDBC call out, network round trip,
 * result materialization, call back). This is the number an end user would
 * actually feel. It is not the same as the execution time Postgres itself
 * reports inside EXPLAIN ANALYZE, which measures only server-side work and
 * excludes network/driver overhead -- we capture that separately.
 */
@Service
public class BenchmarkService {

    private final NamedParameterJdbcTemplate jdbc;

    public BenchmarkService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BenchmarkResult executeTimed(WorkloadQuery query, Map<String, Object> params) {
        long startNanos = System.nanoTime();
        List<Map<String, Object>> rows = jdbc.queryForList(query.sql(), params);
        long endNanos = System.nanoTime();

        double elapsedMs = (endNanos - startNanos) / 1_000_000.0;
        return new BenchmarkResult(query.id(), elapsedMs, rows.size());
    }

    /**
     * Runs the query {@code repetitions} times and reports the median execution
     * time. A single timing sample is noisy (OS scheduling, cache warmth, JIT
     * warmup) -- the median of several runs is far more representative of the
     * query's real cost than any one sample, and is more robust to one-off
     * outliers than a plain average would be.
     */
    public RepeatedBenchmarkResult executeRepeated(WorkloadQuery query, Map<String, Object> params, int repetitions) {
        if (repetitions < 1) {
            throw new IllegalArgumentException("repetitions must be >= 1");
        }
        List<Double> times = new ArrayList<>(repetitions);
        int rowsReturned = 0;
        for (int i = 0; i < repetitions; i++) {
            BenchmarkResult result = executeTimed(query, params);
            times.add(result.executionTimeMs());
            rowsReturned = result.rowsReturned();
        }
        return new RepeatedBenchmarkResult(query.id(), times, median(times), rowsReturned);
    }

    private double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int n = sorted.size();
        int mid = n / 2;
        return (n % 2 == 0) ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0 : sorted.get(mid);
    }
}
