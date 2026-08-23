package com.planshift.querybuilder;

import com.planshift.workload.WorkloadQuery;

import java.util.Map;

public record BuiltQuery(
        WorkloadQuery query,
        Map<String, Object> params
) {
}
