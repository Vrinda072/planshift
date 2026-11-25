package com.planshift.impact;

import com.planshift.experiment.IndexSpec;
import com.planshift.querybuilder.QuerySpec;

/**
 * What decides whether a given index can help a given query, worked out before anything runs and stored with each
 * experiment so later estimates compare against what was true of that run.
 *
 * @param access      how the index could be used: its leading column serves the filter, only the ordering, or neither
 * @param operator    the kind of filter
 * @param selectivity the planner's estimate of the fraction of the table the filter keeps (1.0 without a filter)
 * @param coversOrder the index carries on from the filtered column to the ORDER BY column
 */
public record QueryFeatures(Access access, Operator operator, double selectivity, boolean coversOrder) {

    public enum Access { FILTER, ORDER, NONE }

    public enum Operator { EQUALITY, RANGE, OTHER, NONE }

    public static Operator operatorOf(QuerySpec spec) {
        if (spec.filterColumn() == null) {
            return Operator.NONE;
        }
        return switch (spec.filterOperator()) {
            case "=" -> Operator.EQUALITY;
            case ">", "<", ">=", "<=" -> Operator.RANGE;
            // != matches nearly everything, and a plain btree only serves LIKE under particular collations.
            default -> Operator.OTHER;
        };
    }

    /** Only the leading column decides whether a btree can be used to find rows or to return them in order. */
    public static Access accessOf(QuerySpec spec, IndexSpec index) {
        String leading = index.columns().get(0);
        Operator operator = operatorOf(spec);
        if (leading.equals(spec.filterColumn()) && (operator == Operator.EQUALITY || operator == Operator.RANGE)) {
            return Access.FILTER;
        }
        boolean plainOrdering = spec.aggregateFunction() == null && leading.equals(spec.orderByColumn());
        return plainOrdering ? Access.ORDER : Access.NONE;
    }

    public static boolean coversOrder(QuerySpec spec, IndexSpec index) {
        return operatorOf(spec) == Operator.EQUALITY
                && accessOf(spec, index) == Access.FILTER
                && index.columns().size() > 1
                && index.columns().get(1).equals(spec.orderByColumn());
    }
}
