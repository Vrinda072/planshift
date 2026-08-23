package com.planshift.controller;

import com.planshift.querybuilder.QuerySpec;

public record QueryBuilderRunRequest(
        QuerySpec spec,
        String indexColumn,
        Integer repetitions,
        Double thresholdFraction
) {
}
