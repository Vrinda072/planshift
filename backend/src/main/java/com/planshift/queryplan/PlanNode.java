package com.planshift.queryplan;

import java.util.List;

public record PlanNode(
        String nodeType,
        String relationName,
        String indexName,
        String filter,
        String indexCondition,
        int planRows,
        int actualRows,
        double actualTotalTimeMs,
        List<PlanNode> children
) {
}
