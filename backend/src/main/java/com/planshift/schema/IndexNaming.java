package com.planshift.schema;

/** The one place that decides what an index PLANSHIFT creates (and later drops) is called. */
public final class IndexNaming {

    private IndexNaming() {
    }

    public static String managedName(String table, String column) {
        if ("orders".equals(table) && "customer_id".equals(column)) {
            return "idx_orders_customer_id";
        }
        return "idx_planshift_" + table + "_" + column;
    }
}
