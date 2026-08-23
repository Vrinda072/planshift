package com.planshift.dataimport;

import java.util.List;

public record ImportResult(
        String tableName,
        List<String> columnNames,
        int rowCount
) {
}
