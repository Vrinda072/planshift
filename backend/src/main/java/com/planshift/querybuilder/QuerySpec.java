package com.planshift.querybuilder;

import java.util.List;

/**
 * A structured, non-SQL description of a query: pick a table, pick columns
 * (or an aggregation), optionally filter on one column, optionally order,
 * optionally limit. QueryBuilderService turns this into real SQL after
 * validating every identifier against the live database schema -- there is
 * no path from client input to raw SQL text here.
 */
public record QuerySpec(
        String table,
        List<String> selectColumns,
        String aggregateFunction,
        String aggregateColumn,
        String groupByColumn,
        String filterColumn,
        String filterOperator,
        String filterValue,
        String orderByColumn,
        String orderByDirection,
        Integer limit
) {
}
