package com.planshift.schema;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the real, live Postgres schema via information_schema -- both to
 * power the query builder's table/column pickers, and as the source of
 * truth for validating that a table/column name a client sent us actually
 * exists before it's ever interpolated into SQL (see QuerySpecValidator).
 */
@Service
public class SchemaService {

    /** PLANSHIFT's own bookkeeping tables -- not meant to be queried/experimented on as "data". */
    private static final Set<String> INTERNAL_TABLES = Set.of("experiments", "experiment_query_results");

    private final JdbcTemplate jdbc;

    public SchemaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TableInfo> listTables() {
        Map<String, List<ColumnInfo>> byTable = new LinkedHashMap<>();

        jdbc.query(
                "SELECT table_name, column_name, data_type, is_nullable "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema = 'public' "
                        + "ORDER BY table_name, ordinal_position",
                rs -> {
                    String table = rs.getString("table_name");
                    if (INTERNAL_TABLES.contains(table)) {
                        return;
                    }
                    byTable.computeIfAbsent(table, t -> new java.util.ArrayList<>())
                            .add(new ColumnInfo(
                                    rs.getString("column_name"),
                                    rs.getString("data_type"),
                                    "YES".equals(rs.getString("is_nullable"))));
                });

        return byTable.entrySet().stream()
                .map(e -> new TableInfo(e.getKey(), e.getValue()))
                .toList();
    }

    public boolean tableExists(String tableName) {
        if (INTERNAL_TABLES.contains(tableName)) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    public boolean columnExists(String tableName, String columnName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}
