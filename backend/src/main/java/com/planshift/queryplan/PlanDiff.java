package com.planshift.queryplan;

public record PlanDiff(
        PlanChangeType changeType,
        String path,
        String baselineNodeType,
        String candidateNodeType,
        String description
) {
}
