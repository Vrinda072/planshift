package com.planshift.queryplan;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a raw Postgres EXPLAIN (FORMAT JSON) plan node into our simplified
 * PlanNode tree, keeping only the fields we actually use for comparison and
 * display.
 */
@Component
public class PlanParser {

    public PlanNode parse(JsonNode planJson) {
        String nodeType = planJson.path("Node Type").asText("Unknown");
        String relationName = planJson.hasNonNull("Relation Name") ? planJson.get("Relation Name").asText() : null;
        String indexName = planJson.hasNonNull("Index Name") ? planJson.get("Index Name").asText() : null;
        String filter = planJson.hasNonNull("Filter") ? planJson.get("Filter").asText() : null;
        String indexCondition = planJson.hasNonNull("Index Cond") ? planJson.get("Index Cond").asText() : null;
        int planRows = planJson.path("Plan Rows").asInt(0);
        int actualRows = planJson.path("Actual Rows").asInt(0);
        double actualTotalTimeMs = planJson.path("Actual Total Time").asDouble(0.0);

        List<PlanNode> children = new ArrayList<>();
        if (planJson.has("Plans")) {
            for (JsonNode child : planJson.get("Plans")) {
                children.add(parse(child));
            }
        }

        return new PlanNode(nodeType, relationName, indexName, filter, indexCondition,
                planRows, actualRows, actualTotalTimeMs, children);
    }
}
