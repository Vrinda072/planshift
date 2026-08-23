package com.planshift.querybuilder;

import com.planshift.schema.SchemaService;
import com.planshift.workload.QueryCategory;
import com.planshift.workload.WorkloadQuery;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns a structured {@link QuerySpec} into real, parameterized SQL. Table
 * and column names are validated against the live Postgres schema
 * (information_schema, via SchemaService) before being used -- so even
 * though they end up interpolated into the SQL text (identifiers can't be
 * bind parameters in JDBC), only names that already exist as real
 * tables/columns in this database can ever reach that interpolation. The
 * one user-supplied *value* (the filter value) is always passed as a bound
 * parameter, never concatenated.
 */
@Service
public class QueryBuilderService {

    private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Set<String> ALLOWED_AGGREGATES = Set.of("COUNT", "SUM", "AVG", "MIN", "MAX");
    private static final Set<String> ALLOWED_OPERATORS = Set.of("=", "!=", ">", "<", ">=", "<=", "LIKE");
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 5000;

    private final SchemaService schemaService;

    public QueryBuilderService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    public BuiltQuery build(QuerySpec spec) {
        validateIdentifier(spec.table(), "table");
        if (!schemaService.tableExists(spec.table())) {
            throw new IllegalArgumentException("Unknown table: " + spec.table());
        }

        StringBuilder sql = new StringBuilder("SELECT ");
        boolean usingAggregate = spec.aggregateFunction() != null;

        if (usingAggregate) {
            String fn = spec.aggregateFunction().toUpperCase();
            if (!ALLOWED_AGGREGATES.contains(fn)) {
                throw new IllegalArgumentException("Unsupported aggregate function: " + spec.aggregateFunction());
            }
            List<String> selectParts = new java.util.ArrayList<>();
            if (spec.groupByColumn() != null) {
                validateColumn(spec.table(), spec.groupByColumn());
                selectParts.add(quoteIdent(spec.groupByColumn()));
            }
            if ("COUNT".equals(fn) && (spec.aggregateColumn() == null || "*".equals(spec.aggregateColumn()))) {
                selectParts.add("COUNT(*) AS result");
            } else {
                if (spec.aggregateColumn() == null) {
                    throw new IllegalArgumentException(fn + " requires a column");
                }
                validateColumn(spec.table(), spec.aggregateColumn());
                selectParts.add(fn + "(" + quoteIdent(spec.aggregateColumn()) + ") AS result");
            }
            sql.append(String.join(", ", selectParts));
        } else {
            List<String> cols = (spec.selectColumns() == null || spec.selectColumns().isEmpty())
                    ? List.of("*") : spec.selectColumns();
            if (cols.size() == 1 && "*".equals(cols.get(0))) {
                sql.append("*");
            } else {
                cols.forEach(c -> validateColumn(spec.table(), c));
                sql.append(cols.stream().map(this::quoteIdent).collect(Collectors.joining(", ")));
            }
        }

        sql.append(" FROM ").append(quoteIdent(spec.table()));

        Map<String, Object> params = new LinkedHashMap<>();
        if (spec.filterColumn() != null) {
            validateColumn(spec.table(), spec.filterColumn());
            String operator = spec.filterOperator();
            if (operator == null || !ALLOWED_OPERATORS.contains(operator.toUpperCase())) {
                throw new IllegalArgumentException("Unsupported filter operator: " + operator);
            }
            sql.append(" WHERE ").append(quoteIdent(spec.filterColumn()))
                    .append(" ").append(operator.toUpperCase()).append(" :filterValue");
            params.put("filterValue", parseValue(spec.filterValue()));
        }

        if (usingAggregate && spec.groupByColumn() != null) {
            sql.append(" GROUP BY ").append(quoteIdent(spec.groupByColumn()));
        }

        if (spec.orderByColumn() != null) {
            String orderExpr;
            if (usingAggregate && "result".equalsIgnoreCase(spec.orderByColumn())) {
                orderExpr = "result";
            } else {
                validateColumn(spec.table(), spec.orderByColumn());
                orderExpr = quoteIdent(spec.orderByColumn());
            }
            String direction = "DESC".equalsIgnoreCase(spec.orderByDirection()) ? "DESC" : "ASC";
            sql.append(" ORDER BY ").append(orderExpr).append(" ").append(direction);
        }

        int limit = spec.limit() == null ? DEFAULT_LIMIT : Math.min(Math.max(spec.limit(), 1), MAX_LIMIT);
        sql.append(" LIMIT ").append(limit);

        String queryId = "CUSTOM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        WorkloadQuery query = new WorkloadQuery(queryId, "Custom query on " + spec.table(),
                QueryCategory.CUSTOM, "Built with the query builder.", sql.toString());

        return new BuiltQuery(query, params);
    }

    private void validateColumn(String table, String column) {
        validateIdentifier(column, "column");
        if (!schemaService.columnExists(table, column)) {
            throw new IllegalArgumentException("Unknown column: " + table + "." + column);
        }
    }

    private void validateIdentifier(String value, String label) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + label + " name: " + value);
        }
    }

    private String quoteIdent(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /** Filter values come in as strings from the UI; bind them as the most specific type we can infer. */
    private Object parseValue(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            // not an integer
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            // not a number at all -- treat as text
        }
        return raw;
    }
}
