package com.planshift.dataimport;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Imports a CSV file as a new Postgres table: infers a column type per
 * column by scanning every row (not just a sample -- Postgres's COPY will
 * hard-fail the whole import if even one row doesn't match a declared
 * column type, so a partial sample isn't safe here), creates the table,
 * then hands the raw CSV to Postgres's own COPY command to load it --
 * COPY's CSV parser is more correct than anything worth hand-rolling here
 * for quoted fields, embedded commas, etc.
 *
 * The upload is spooled to a temp file and read line by line twice (type
 * inference, then COPY), so memory use doesn't grow with file size.
 */
@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);
    private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Set<String> RESERVED_TABLE_NAMES = Set.of(
            "customers", "products", "orders", "order_items", "experiments", "experiment_query_results");

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public CsvImportService(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    public ImportResult importCsv(String requestedTableName, MultipartFile file) throws IOException {
        String tableName = sanitizeTableName(requestedTableName);
        if (RESERVED_TABLE_NAMES.contains(tableName)) {
            throw new IllegalArgumentException("'" + tableName + "' is a built-in PLANSHIFT table name and can't be overwritten by an import.");
        }

        java.nio.file.Path temp = java.nio.file.Files.createTempFile("planshift-import-", ".csv");
        try {
            file.transferTo(temp);

            List<String> columnNames;
            List<String> columnTypes;
            try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(temp, StandardCharsets.UTF_8)) {
                String header = reader.readLine();
                if (header == null || header.isBlank()) {
                    throw new IllegalArgumentException("CSV file is empty");
                }
                columnNames = sanitizeColumnNames(splitCsvLine(stripBom(header)));
                columnTypes = inferColumnTypes(columnNames.size(), reader);
            }

            createTable(tableName, columnNames, columnTypes);
            loadViaCopy(tableName, temp);

            int rowCount = jdbc.queryForObject("SELECT COUNT(*) FROM " + quoteIdent(tableName), Integer.class);
            log.info("Imported CSV into table {}: {} columns, {} rows", tableName, columnNames.size(), rowCount);
            return new ImportResult(tableName, columnNames, rowCount);
        } finally {
            java.nio.file.Files.deleteIfExists(temp);
        }
    }

    private static String stripBom(String line) {
        return line.startsWith("\uFEFF") ? line.substring(1) : line;
    }

    private void createTable(String tableName, List<String> columnNames, List<String> columnTypes) {
        jdbc.execute("DROP TABLE IF EXISTS " + quoteIdent(tableName));
        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(quoteIdent(tableName)).append(" (");
        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0) {
                ddl.append(", ");
            }
            ddl.append(quoteIdent(columnNames.get(i))).append(" ").append(columnTypes.get(i));
        }
        ddl.append(")");
        jdbc.execute(ddl.toString());
    }

    private void loadViaCopy(String tableName, java.nio.file.Path csv) {
        try (Connection connection = dataSource.getConnection();
             java.io.InputStream in = java.nio.file.Files.newInputStream(csv)) {
            PGConnection pgConnection = connection.unwrap(PGConnection.class);
            CopyManager copyManager = pgConnection.getCopyAPI();
            copyManager.copyIn(
                    "COPY " + quoteIdent(tableName) + " FROM STDIN WITH (FORMAT csv, HEADER true)",
                    in);
        } catch (SQLException | IOException e) {
            throw new IllegalArgumentException("Failed to load CSV data: " + e.getMessage(), e);
        }
    }

    private List<String> inferColumnTypes(int columnCount, java.io.BufferedReader reader) throws IOException {
        boolean[] couldBeInteger = new boolean[columnCount];
        boolean[] couldBeNumeric = new boolean[columnCount];
        java.util.Arrays.fill(couldBeInteger, true);
        java.util.Arrays.fill(couldBeNumeric, true);

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            List<String> row = splitCsvLine(line);
            for (int c = 0; c < columnCount; c++) {
                String value = c < row.size() ? row.get(c) : "";
                if (value.isBlank()) {
                    continue;
                }
                if (couldBeInteger[c]) {
                    try {
                        Long.parseLong(value.trim());
                    } catch (NumberFormatException e) {
                        couldBeInteger[c] = false;
                    }
                }
                if (couldBeNumeric[c]) {
                    try {
                        Double.parseDouble(value.trim());
                    } catch (NumberFormatException e) {
                        couldBeNumeric[c] = false;
                    }
                }
            }
        }

        List<String> types = new ArrayList<>(columnCount);
        for (int c = 0; c < columnCount; c++) {
            if (couldBeInteger[c]) {
                types.add("BIGINT");
            } else if (couldBeNumeric[c]) {
                types.add("DOUBLE PRECISION");
            } else {
                types.add("TEXT");
            }
        }
        return types;
    }

    private String sanitizeTableName(String raw) {
        String base = raw == null ? "" : raw.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        if (base.isEmpty() || Character.isDigit(base.charAt(0))) {
            base = "t_" + base;
        }
        if (base.length() > 63) {
            base = base.substring(0, 63);
        }
        if (!IDENTIFIER.matcher(base).matches()) {
            throw new IllegalArgumentException("Could not derive a valid table name from: " + raw);
        }
        return base;
    }

    private List<String> sanitizeColumnNames(List<String> rawHeaders) {
        Set<String> used = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String raw : rawHeaders) {
            String base = raw.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
            if (base.isEmpty() || Character.isDigit(base.charAt(0))) {
                base = "c_" + base;
            }
            if (base.length() > 63) {
                base = base.substring(0, 63);
            }
            String candidate = base;
            int suffix = 2;
            while (used.contains(candidate)) {
                candidate = base + "_" + suffix++;
            }
            used.add(candidate);
            result.add(candidate);
        }
        return result;
    }

    /** Minimal RFC4180-style split: handles quoted fields, embedded commas, and "" as an escaped quote. */
    private List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(ch);
                }
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private String quoteIdent(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
