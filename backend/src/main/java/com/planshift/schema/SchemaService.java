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
        Set<String> indexedColumns = new java.util.HashSet<>();
        // Only the leading column of an index can serve a plain lookup, so that is
        // the one we report. Indexes PLANSHIFT manages are left out: an experiment
        // drops those for the baseline, so they say nothing about the column's
        // state going in. What remains (a primary key, an index you made yourself)
        // stays in place and means the baseline is already indexed.
        jdbc.query(
                "SELECT c.relname AS table_name, a.attname AS column_name, ic.relname AS index_name "
                        + "FROM pg_index i "
                        + "JOIN pg_class c ON c.oid = i.indrelid "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "JOIN pg_class ic ON ic.oid = i.indexrelid "
                        + "JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = i.indkey[0] "
                        + "WHERE n.nspname = 'public'",
                rs -> {
                    String table = rs.getString("table_name");
                    String column = rs.getString("column_name");
                    if (!IndexNaming.isManaged(rs.getString("index_name"))) {
                        indexedColumns.add(table + "." + column);
                    }
                });

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
                    String column = rs.getString("column_name");
                    byTable.computeIfAbsent(table, t -> new java.util.ArrayList<>())
                            .add(new ColumnInfo(
                                    column,
                                    rs.getString("data_type"),
                                    "YES".equals(rs.getString("is_nullable")),
                                    indexedColumns.contains(table + "." + column)));
                });

        return byTable.entrySet().stream()
                .map(e -> new TableInfo(e.getKey(), e.getValue(), rowEstimate(e.getKey())))
                .toList();
    }

    /**
     * The planner's own estimate (pg_class.reltuples) is free; it is -1 for a
     * table that has never been analyzed, which is exactly the state of a
     * freshly imported CSV, so fall back to a real count in that case.
     */
    private long rowEstimate(String table) {
        Long estimate = jdbc.queryForObject(
                "SELECT c.reltuples::bigint FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relname = ?",
                Long.class, table);
        if (estimate != null && estimate >= 0) {
            return estimate;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM \"" + table.replace("\"", "\"\"") + "\"", Long.class);
        return count == null ? 0 : count;
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
