package com.planshift.experiment;

import java.util.List;

/** What an experiment adds: an index method (btree or hash) over one or more columns, in order. */
public record IndexSpec(String method, List<String> columns) {

    public static final int MAX_COLUMNS = 4;

    public IndexSpec {
        method = method == null || method.isBlank() ? "btree" : method.trim().toLowerCase();
        if (!method.equals("btree") && !method.equals("hash")) {
            throw new IllegalArgumentException("Unsupported index type: " + method + " (use btree or hash)");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("Pick at least one column to index");
        }
        if (columns.size() > MAX_COLUMNS) {
            throw new IllegalArgumentException("An index can cover at most " + MAX_COLUMNS + " columns here");
        }
        if (columns.stream().distinct().count() != columns.size()) {
            throw new IllegalArgumentException("Each column can appear only once in an index");
        }
        if (method.equals("hash") && columns.size() > 1) {
            throw new IllegalArgumentException("Hash indexes cover a single column; use btree for a composite index");
        }
        columns = List.copyOf(columns);
    }

    public static IndexSpec btree(String column) {
        return new IndexSpec("btree", List.of(column));
    }

    /** Comma-joined, the form stored in experiments.target_column. */
    public String columnsKey() {
        return String.join(",", columns);
    }

    public static List<String> splitKey(String key) {
        return key == null ? List.of() : List.of(key.split(","));
    }
}
