package com.planshift.workload;

public enum QueryCategory {
    SELECTIVE_FILTER,
    NON_SELECTIVE_FILTER,
    DATE_RANGE,
    JOIN,
    AGGREGATION,
    GROUP_BY,
    ORDER_BY,
    LOOKUP,
    MULTI_TABLE,
    CUSTOM
}
