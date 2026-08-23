package com.planshift.queryplan;

import com.fasterxml.jackson.databind.JsonNode;

public record ExplainCapture(
        String queryId,
        double planningTimeMs,
        double executionTimeMs,
        JsonNode planRoot,
        String rawJson
) {
}
