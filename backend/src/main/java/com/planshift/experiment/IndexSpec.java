package com.planshift.experiment;

import java.util.List;

/** What an experiment adds: a btree index over one or more columns, in order. */
public record IndexSpec(List<String> columns) {

    public static final int MAX_COLUMNS = 4;

    public IndexSpec {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("Pick at least one column to index");
        }
        if (columns.size() > MAX_COLUMNS) {
            throw new IllegalArgumentException("An index can cover at most " + MAX_COLUMNS + " columns here");
        }
        if (columns.stream().distinct().count() != columns.size()) {
            throw new IllegalArgumentException("Each column can appear only once in an index");
        }
        columns = List.copyOf(columns);
    }

    public static IndexSpec of(String column) {
        return new IndexSpec(List.of(column));
    }

    /** Comma-joined, the form stored in experiments.target_column. */
    public String columnsKey() {
        return String.join(",", columns);
    }
}
