package com.planshift.experiment;

import org.springframework.jdbc.core.SqlParameterValue;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes a query's values into its named placeholders so a person can read what was measured. For display only: the
 * result is stored and shown, and never run. Execution always binds the values.
 */
final class DisplaySql {

    /** A named placeholder, not the second colon of a cast such as {@code ::date}. */
    private static final Pattern PLACEHOLDER = Pattern.compile("(?<!:):([A-Za-z]\\w*)");

    private DisplaySql() {
    }

    static String of(String sql, Map<String, Object> params) {
        Matcher matcher = PLACEHOLDER.matcher(sql);
        return matcher.replaceAll(match -> Matcher.quoteReplacement(
                params.containsKey(match.group(1)) ? literal(params.get(match.group(1))) : match.group()));
    }

    private static String literal(Object value) {
        Object plain = value instanceof SqlParameterValue typed ? typed.getValue() : value;
        if (plain == null) {
            return "NULL";
        }
        if (plain instanceof Number || plain instanceof Boolean) {
            return plain.toString();
        }
        return "'" + plain.toString().replace("'", "''") + "'";
    }
}
