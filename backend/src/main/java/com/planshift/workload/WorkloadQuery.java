package com.planshift.workload;

/**
 * A predefined, read-only SQL query in the benchmark workload. Uses named
 * parameters (e.g. :customerId) resolved through NamedParameterJdbcTemplate --
 * never string-concatenated SQL, so this stays safe even though the values
 * driving it are fixed, not user-supplied.
 */
public record WorkloadQuery(
        String id,
        String name,
        QueryCategory category,
        String description,
        String sql
) {
}
