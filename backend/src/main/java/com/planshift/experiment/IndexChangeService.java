package com.planshift.experiment;

import com.planshift.schema.SchemaService;
import com.planshift.schema.IndexNaming;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
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
    private final TransactionTemplate transactions;

    public IndexChangeService(JdbcTemplate jdbc, SchemaService schemaService, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.schemaService = schemaService;
        this.transactions = transactions;
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
        jdbc.execute("CREATE INDEX IF NOT EXISTS " + quoteIdent(nameOf(table, spec))
                + " ON " + quoteIdent(table) + " (" + columnList(spec) + ")");
    }

    public void dropIndex(String table, IndexSpec spec) {
        requireValid(table, spec);
        jdbc.execute("DROP INDEX IF EXISTS " + quoteIdent(nameOf(table, spec)));
    }

    /**
     * Creates this experiment's own, uniquely named index and records that it owns it, atomically: Postgres DDL is
     * transactional, so there is no moment where the index exists without the record or the other way round.
     * No IF NOT EXISTS on purpose -- if the name were somehow taken, this must fail rather than adopt (and later
     * drop) an index it didn't create.
     */
    public String createExperimentIndex(long experimentId, String table, IndexSpec spec) {
        requireValid(table, spec);
        String name = IndexNaming.experimentName(experimentId);
        transactions.executeWithoutResult(status -> {
            jdbc.execute("CREATE INDEX " + quoteIdent(name) + " ON " + quoteIdent(table)
                    + " (" + columnList(spec) + ")");
            jdbc.update("UPDATE experiments SET index_name = ? WHERE experiment_id = ?", name, experimentId);
        });
        return name;
    }

    /** Drops every index still recorded as owned by an experiment. Nothing is ever dropped on the strength of its name. */
    public void dropRecordedIndexes() {
        jdbc.query("SELECT experiment_id, index_name FROM experiments WHERE index_name IS NOT NULL",
                        (rs, row) -> Map.entry(rs.getLong("experiment_id"), rs.getString("index_name")))
                .forEach(owned -> transactions.executeWithoutResult(status -> {
                    jdbc.execute("DROP INDEX IF EXISTS " + quoteIdent(owned.getValue()));
                    jdbc.update("UPDATE experiments SET index_name = NULL WHERE experiment_id = ?", owned.getKey());
                }));
    }

    /**
     * One-time cleanup for experiments run before ownership was recorded: they left their index behind under a name
     * derived from the table and columns. Only a name matching a stored experiment, on that same table, is dropped.
     */
    public void dropLegacyIndexes() {
        List<LegacyTarget> targets = jdbc.query(
                "SELECT experiment_id, target_table, target_column FROM experiments WHERE legacy_index_pending",
                (rs, row) -> new LegacyTarget(rs.getLong("experiment_id"), rs.getString("target_table"),
                        rs.getString("target_column")));
        for (LegacyTarget target : targets) {
            String table = target.table() != null ? target.table() : "orders";
            String columns = target.columns() != null ? target.columns() : "customer_id";
            String name = IndexNaming.managedName(table, List.of(columns.split(",")));
            transactions.executeWithoutResult(status -> {
                Integer matches = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND tablename = ? AND indexname = ?",
                        Integer.class, table, name);
                if (matches != null && matches > 0) {
                    jdbc.execute("DROP INDEX " + quoteIdent(name));
                }
                jdbc.update("UPDATE experiments SET legacy_index_pending = FALSE WHERE experiment_id = ?", target.id());
            });
        }
    }

    private record LegacyTarget(long id, String table, String columns) {
    }

    private String columnList(IndexSpec spec) {
        return spec.columns().stream().map(this::quoteIdent).collect(Collectors.joining(", "));
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
