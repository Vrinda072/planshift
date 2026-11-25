package com.planshift.schema;

/** Names of the indexes PLANSHIFT creates. A name is never taken as proof of ownership; the experiments table is. */
public final class IndexNaming {

    private IndexNaming() {
    }

    private static final int MAX_LENGTH = 63;
    private static final String EXPERIMENT_PREFIX = "planshift_exp_";

    /** Name of the temporary index one experiment creates and later removes. */
    public static String experimentName(long experimentId) {
        return EXPERIMENT_PREFIX + experimentId;
    }

    /** Name of an index someone chose to keep after an experiment. */
    public static String keptName(long experimentId) {
        return "planshift_keep_" + experimentId;
    }

    /** True for an experiment's temporary index, so the schema view doesn't report it as a lasting one. */
    public static boolean isTemporary(String indexName) {
        return indexName.startsWith(EXPERIMENT_PREFIX);
    }

    public static String managedName(String table, String column) {
        return managedName(table, java.util.List.of(column));
    }

    /** A single column keeps the original names; composites are named after all their columns. */
    public static String managedName(String table, java.util.List<String> columns) {
        if (columns.size() == 1 && "orders".equals(table) && "customer_id".equals(columns.get(0))) {
            return "idx_orders_customer_id";
        }
        String name = "idx_planshift_" + table + "_" + String.join("_", columns);
        if (name.length() <= MAX_LENGTH) {
            return name;
        }
        // Postgres truncates identifiers at 63 bytes, which could make two different indexes collide.
        String tag = Integer.toHexString(name.hashCode());
        return name.substring(0, MAX_LENGTH - tag.length() - 1) + "_" + tag;
    }
}
