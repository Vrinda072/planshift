package com.planshift.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planshift.workload.WorkloadQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes a read-only workload query against Postgres and times it. By
 * default from the caller's side (wall-clock: JDBC call out, network round trip,
 * result materialization, call back). This is the number an end user would
 * actually feel. It is not the same as the execution time Postgres itself
 * reports inside EXPLAIN ANALYZE, which measures only server-side work and
 * excludes network/driver overhead -- we capture that separately, and
 * {@link TimingSource#DATABASE} switches the repeated runs over to it.
 */
@Service
public class BenchmarkService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TimingSource timingSource;

    public BenchmarkService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper,
                            @Value("${planshift.benchmark.timing-source:app}") TimingSource timingSource) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.timingSource = timingSource;
    }

    public TimingSource timingSource() {
        return timingSource;
    }

    public BenchmarkResult executeTimed(WorkloadQuery query, Map<String, Object> params) {
        if (timingSource == TimingSource.DATABASE) {
            return executeTimedByDatabase(query, params);
        }
        long startNanos = System.nanoTime();
        List<Map<String, Object>> rows = jdbc.queryForList(query.sql(), params);
        long endNanos = System.nanoTime();

        double elapsedMs = (endNanos - startNanos) / 1_000_000.0;
        return new BenchmarkResult(query.id(), elapsedMs, rows.size());
    }

    private BenchmarkResult executeTimedByDatabase(WorkloadQuery query, Map<String, Object> params) {
        String json = jdbc.queryForObject(
                "EXPLAIN (ANALYZE, TIMING OFF, FORMAT JSON) " + query.sql(), params, String.class);
        try {
            JsonNode result = objectMapper.readTree(json).get(0);
            return new BenchmarkResult(query.id(), result.get("Execution Time").asDouble(),
                    result.get("Plan").path("Actual Rows").asInt());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read Postgres's execution time for query " + query.id(), e);
        }
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
        return new RepeatedBenchmarkResult(query.id(), times, RepeatedBenchmarkResult.median(times), rowsReturned);
    }
}
