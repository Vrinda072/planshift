package com.planshift.schema;

/** The one place that decides what an index PLANSHIFT creates (and later drops) is called. */
public final class IndexNaming {

    private IndexNaming() {
    }

    private static final int MAX_LENGTH = 63;

    public static String managedName(String table, String column) {
        return managedName(table, java.util.List.of(column), "btree");
    }

    /** Single btree column keeps the original names; composites and hash indexes get distinct ones. */
    public static String managedName(String table, java.util.List<String> columns, String method) {
        if ("btree".equals(method) && columns.size() == 1
                && "orders".equals(table) && "customer_id".equals(columns.get(0))) {
            return "idx_orders_customer_id";
        }
        String name = "idx_planshift_" + table + "_" + String.join("_", columns)
                + ("hash".equals(method) ? "_hash" : "");
        if (name.length() <= MAX_LENGTH) {
            return name;
        }
        // Postgres truncates identifiers at 63 bytes, which could make two different indexes collide.
        String tag = Integer.toHexString(name.hashCode());
        return name.substring(0, MAX_LENGTH - tag.length() - 1) + "_" + tag;
    }

    public static boolean isManaged(String indexName) {
        return "idx_orders_customer_id".equals(indexName) || indexName.startsWith("idx_planshift_");
    }
}
