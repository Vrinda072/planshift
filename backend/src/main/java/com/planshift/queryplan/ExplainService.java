package com.planshift.queryplan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planshift.workload.WorkloadQuery;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Runs EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) against a workload query.
 * ANALYZE means the query is actually executed -- so this must only ever be
 * called with read-only queries from WorkloadCatalog, never arbitrary SQL.
 */
@Service
public class ExplainService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ExplainService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public ExplainCapture capture(WorkloadQuery query, Map<String, Object> params) {
        String explainSql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + query.sql();
        String rawJson = jdbc.queryForObject(explainSql, params, String.class);

        try {
            JsonNode resultArray = objectMapper.readTree(rawJson);
            JsonNode topLevel = resultArray.get(0);
            JsonNode planRoot = topLevel.get("Plan");
            double planningTimeMs = topLevel.path("Planning Time").asDouble(0.0);
            double executionTimeMs = topLevel.path("Execution Time").asDouble(0.0);

            return new ExplainCapture(query.id(), planningTimeMs, executionTimeMs, planRoot, rawJson);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse EXPLAIN JSON output for query " + query.id(), e);
        }
    }
}
