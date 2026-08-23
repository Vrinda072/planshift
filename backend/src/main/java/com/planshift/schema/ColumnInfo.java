package com.planshift.schema;

public record ColumnInfo(
        String columnName,
        String dataType,
        boolean nullable
) {
}
