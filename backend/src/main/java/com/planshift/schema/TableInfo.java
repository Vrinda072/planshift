package com.planshift.schema;

import java.util.List;

public record TableInfo(
        String tableName,
        List<ColumnInfo> columns
) {
}
