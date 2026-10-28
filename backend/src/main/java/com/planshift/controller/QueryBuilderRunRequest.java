package com.planshift.controller;

import com.planshift.querybuilder.QuerySpec;

public record QueryBuilderRunRequest(
        QuerySpec spec,
        String indexColumn,
        java.util.List<String> indexColumns,
        String indexMethod,
        Integer repetitions,
        Double thresholdFraction
) {
}
