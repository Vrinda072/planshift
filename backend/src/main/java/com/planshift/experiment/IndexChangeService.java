package com.planshift.experiment;

import com.planshift.schema.SchemaService;
import com.planshift.schema.IndexNaming;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Adds/removes an index on a given table.column -- the one supported
 * "database change" in this MVP. Table and column names are always
 * validated against the live schema (information_schema) before being
 * used in DDL, since they end up interpolated into SQL (values never do --
 * only identifiers, and only after being confirmed to already exist in the
 * database, which is what makes that safe).
 */
@Service
public class IndexChangeService {

    private final JdbcTemplate jdbc;
    private final SchemaService schemaService;

    public IndexChangeService(JdbcTemplate jdbc, SchemaService schemaService) {
        this.jdbc = jdbc;
        this.schemaService = schemaService;
    }

    public void addCustomerIdIndex() {
        addIndex("orders", "customer_id");
    }

    public void dropCustomerIdIndex() {
        dropIndex("orders", "customer_id");
    }

    public boolean customerIdIndexExists() {
        return indexExists("orders", "customer_id");
    }

    public void addIndex(String table, String column) {
        addIndex(table, IndexSpec.of(column));
    }

    public void dropIndex(String table, String column) {
        dropIndex(table, IndexSpec.of(column));
    }

    public boolean indexExists(String table, String column) {
        requireValid(table, column);
        return indexExists(IndexNaming.managedName(table, List.of(column)));
    }

    public void addIndex(String table, IndexSpec spec) {
        requireValid(table, spec);
        String columns = spec.columns().stream().map(this::quoteIdent).collect(Collectors.joining(", "));
        jdbc.execute("CREATE INDEX IF NOT EXISTS " + quoteIdent(nameOf(table, spec))
                + " ON " + quoteIdent(table) + " (" + columns + ")");
    }

    public void dropIndex(String table, IndexSpec spec) {
        requireValid(table, spec);
        jdbc.execute("DROP INDEX IF EXISTS " + quoteIdent(nameOf(table, spec)));
    }

    /**
     * Clears the baseline: drops every PLANSHIFT-created index on this table that leads with the same column as
     * the index under test, including ones left over from earlier experiments (a single-column index from a previous
     * run would otherwise serve a composite experiment's "before" and hide the real effect). Indexes the user
     * created themselves are never touched.
     */
    public void dropManagedIndexesLeadingWith(String table, IndexSpec spec) {
        requireValid(table, spec);
        List<String> names = jdbc.queryForList(
                "SELECT ic.relname FROM pg_index i "
                        + "JOIN pg_class c ON c.oid = i.indrelid "
                        + "JOIN pg_class ic ON ic.oid = i.indexrelid "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = i.indkey[0] "
                        + "WHERE n.nspname = 'public' AND c.relname = ? AND a.attname = ?",
                String.class, table, spec.columns().get(0));
        for (String name : names) {
            if (IndexNaming.isManaged(name)) {
                jdbc.execute("DROP INDEX IF EXISTS " + quoteIdent(name));
            }
        }
    }

    private boolean indexExists(String name) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?", Integer.class, name);
        return count != null && count > 0;
    }

    private String nameOf(String table, IndexSpec spec) {
        return IndexNaming.managedName(table, spec.columns());
    }

    private void requireValid(String table, IndexSpec spec) {
        for (String column : spec.columns()) {
            requireValid(table, column);
        }
    }

    private void requireValid(String table, String column) {
        if (!schemaService.tableExists(table)) {
            throw new IllegalArgumentException("Unknown table: " + table);
        }
        if (!schemaService.columnExists(table, column)) {
            throw new IllegalArgumentException("Unknown column: " + table + "." + column);
        }
    }

    private String quoteIdent(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
