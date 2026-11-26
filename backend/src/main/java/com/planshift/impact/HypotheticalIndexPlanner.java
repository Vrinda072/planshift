package com.planshift.impact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planshift.experiment.IndexSpec;
import com.planshift.lock.DatabaseLock;
import com.planshift.querybuilder.BuiltQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.stream.Collectors;

/**
 * Asks Postgres's own planner what it would do if an index existed, using the HypoPG extension. Nothing is built or
 * executed: HypoPG only makes the planner believe in the index, for plain EXPLAIN, in one database session.
 */
@Component
public class HypotheticalIndexPlanner {

    private static final Logger log = LoggerFactory.getLogger(HypotheticalIndexPlanner.class);

    /** The planner's total cost for the query without and with the index, and whether its new plan uses it. */
    public record Costs(double current, double withIndex, boolean usesIndex) {
    }

    /** Either costs, or why there are none. Both are null where HypoPG simply isn't available. */
    public record Answer(Costs costs, String unavailableBecause) {
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final TransactionTemplate transactions;
    private final DatabaseLock databaseLock;
    private final ObjectMapper objectMapper;
    private final boolean mayCreateExtension;
    private volatile boolean installed;
    private volatile boolean creationFailed;

    public HypotheticalIndexPlanner(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc,
                                    TransactionTemplate transactions, DatabaseLock databaseLock,
                                    ObjectMapper objectMapper,
                                    @Value("${planshift.estimates.create-hypopg:true}") boolean mayCreateExtension) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.transactions = transactions;
        this.databaseLock = databaseLock;
        this.objectMapper = objectMapper;
        this.mayCreateExtension = mayCreateExtension;
    }

    /** The table and columns must already have been checked against the schema. */
    public Answer ask(BuiltQuery query, String table, IndexSpec index) {
        try {
            if (!available()) {
                return new Answer(null, null);
            }
            return transactions.execute(status -> {
                status.setRollbackOnly();
                // A running experiment has a real index in place, which would be counted as already there. Holding
                // the lock in shared mode keeps experiments out for these few milliseconds, not other estimates.
                if (!databaseLock.tryShareInCurrentTransaction(jdbc)) {
                    return new Answer(null, "an experiment or another change is using the database");
                }
                // Hypothetical indexes belong to the session and survive rollbacks, so they are cleared explicitly,
                // and the savepoint makes that possible even after a failed statement.
                Object savepoint = status.createSavepoint();
                try {
                    jdbc.execute("SELECT hypopg_reset()");
                    double current = plan(query).path("Total Cost").asDouble();
                    String name = jdbc.queryForObject("SELECT indexname FROM hypopg_create_index(?)", String.class,
                            "CREATE INDEX ON " + quoteIdent(table) + " ("
                                    + index.columns().stream().map(this::quoteIdent).collect(Collectors.joining(", "))
                                    + ")");
                    JsonNode hypothetical = plan(query);
                    return new Answer(new Costs(current, hypothetical.path("Total Cost").asDouble(),
                            uses(hypothetical, name)), null);
                } catch (RuntimeException e) {
                    status.rollbackToSavepoint(savepoint);
                    throw e;
                } finally {
                    jdbc.execute("SELECT hypopg_reset()");
                }
            });
        } catch (DataAccessException e) {
            // An estimate is optional: fall back rather than fail. Look again next time, in case it was removed.
            installed = false;
            log.warn("Could not ask the planner about a hypothetical index", e);
            return new Answer(null, null);
        }
    }

    private boolean available() {
        if (installed) {
            return true;
        }
        if (exists("SELECT COUNT(*) FROM pg_extension WHERE extname = 'hypopg'")) {
            installed = true;
        } else if (mayCreateExtension && !creationFailed
                && exists("SELECT COUNT(*) FROM pg_available_extensions WHERE name = 'hypopg'")) {
            try {
                jdbc.execute("CREATE EXTENSION IF NOT EXISTS hypopg");
                installed = true;
            } catch (DataAccessException e) {
                creationFailed = true;
                log.warn("HypoPG is on this server but could not be enabled; estimates will not use the planner", e);
            }
        }
        return installed;
    }

    private boolean exists(String countSql) {
        Integer count = jdbc.queryForObject(countSql, Integer.class);
        return count != null && count > 0;
    }

    private JsonNode plan(BuiltQuery query) {
        String json = namedJdbc.queryForObject("EXPLAIN (FORMAT JSON) " + query.query().sql(), query.params(),
                String.class);
        try {
            return objectMapper.readTree(json).get(0).get("Plan");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse EXPLAIN JSON output while asking the planner", e);
        }
    }

    private static boolean uses(JsonNode node, String indexName) {
        if (indexName.equals(node.path("Index Name").asText(null))) {
            return true;
        }
        for (JsonNode child : node.path("Plans")) {
            if (uses(child, indexName)) {
                return true;
            }
        }
        return false;
    }

    private String quoteIdent(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
