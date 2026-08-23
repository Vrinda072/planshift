package com.planshift.experiment;

import com.planshift.schema.SchemaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
        requireValid(table, column);
        jdbc.execute("CREATE INDEX IF NOT EXISTS " + quoteIdent(indexName(table, column))
                + " ON " + quoteIdent(table) + " (" + quoteIdent(column) + ")");
    }

    public void dropIndex(String table, String column) {
        requireValid(table, column);
        jdbc.execute("DROP INDEX IF EXISTS " + quoteIdent(indexName(table, column)));
    }

    public boolean indexExists(String table, String column) {
        requireValid(table, column);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?", Integer.class, indexName(table, column));
        return count != null && count > 0;
    }

    private String indexName(String table, String column) {
        if ("orders".equals(table) && "customer_id".equals(column)) {
            return "idx_orders_customer_id";
        }
        return "idx_planshift_" + table + "_" + column;
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
