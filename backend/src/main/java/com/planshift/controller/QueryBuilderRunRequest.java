package com.planshift.controller;

import com.planshift.querybuilder.QuerySpec;

public record QueryBuilderRunRequest(
        QuerySpec spec,
        String indexColumn,
        java.util.List<String> indexColumns,
        Integer repetitions,
        Double thresholdFraction
) {
}
