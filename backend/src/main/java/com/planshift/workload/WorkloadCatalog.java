package com.planshift.workload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fixed set of predefined benchmark queries. The public API only ever
 * runs queries from this catalog -- arbitrary SQL from a client is never
 * accepted, which is what keeps EXPLAIN ANALYZE (which actually executes
 * the query) safe to expose.
 */
public final class WorkloadCatalog {

    private static final List<WorkloadQuery> QUERIES = List.of(
            new WorkloadQuery(
                    "Q1_SELECTIVE_CUSTOMER_LOOKUP",
                    "Selective customer lookup",
                    QueryCategory.SELECTIVE_FILTER,
                    "Look up a single customer by primary key.",
                    "SELECT * FROM customers WHERE customer_id = :customerId"
            ),
            new WorkloadQuery(
                    "Q2_NON_SELECTIVE_CUSTOMER_LOOKUP",
                    "Non-selective customer lookup",
                    QueryCategory.NON_SELECTIVE_FILTER,
                    "Look up all customers in a country -- matches a large fraction of rows.",
                    "SELECT * FROM customers WHERE country = :country"
            ),
            new WorkloadQuery(
                    "Q3_ORDER_HISTORY_LOOKUP",
                    "Order history lookup",
                    QueryCategory.SELECTIVE_FILTER,
                    "All orders for one customer, newest first. This is the query Experiment 1 targets: it filters on orders.customer_id, the column with no index in the baseline schema.",
                    "SELECT * FROM orders WHERE customer_id = :customerId ORDER BY order_date DESC"
            ),
            new WorkloadQuery(
                    "Q4_DATE_RANGE",
                    "Date-range query",
                    QueryCategory.DATE_RANGE,
                    "Orders placed within a date window.",
                    "SELECT * FROM orders WHERE order_date BETWEEN :startDate AND :endDate"
            ),
            new WorkloadQuery(
                    "Q5_JOIN_ORDERS_CUSTOMERS",
                    "Join query",
                    QueryCategory.JOIN,
                    "Orders with a given status, joined to the customer who placed them.",
                    "SELECT o.order_id, c.first_name, c.last_name, o.total_amount "
                            + "FROM orders o JOIN customers c ON o.customer_id = c.customer_id "
                            + "WHERE o.status = :status"
            ),
            new WorkloadQuery(
                    "Q6_AGGREGATION",
                    "Aggregation",
                    QueryCategory.AGGREGATION,
                    "Count, sum, and average order value for a given status.",
                    "SELECT COUNT(*) AS order_count, SUM(total_amount) AS total, AVG(total_amount) AS average "
                            + "FROM orders WHERE status = :status"
            ),
            new WorkloadQuery(
                    "Q7_GROUP_BY_STATUS",
                    "Group by order status",
                    QueryCategory.GROUP_BY,
                    "Order count and revenue broken down by status.",
                    "SELECT status, COUNT(*) AS order_count, SUM(total_amount) AS total "
                            + "FROM orders GROUP BY status"
            ),
            new WorkloadQuery(
                    "Q8_TOP_ORDERS_BY_VALUE",
                    "Order by total value",
                    QueryCategory.ORDER_BY,
                    "The 20 highest-value orders.",
                    "SELECT order_id, customer_id, total_amount FROM orders ORDER BY total_amount DESC LIMIT 20"
            ),
            new WorkloadQuery(
                    "Q9_PRODUCT_LOOKUP_BY_CATEGORY",
                    "Product lookup",
                    QueryCategory.LOOKUP,
                    "All products in a category.",
                    "SELECT * FROM products WHERE category = :category"
            ),
            new WorkloadQuery(
                    "Q10_REVENUE_BY_CATEGORY",
                    "Multi-table revenue by category",
                    QueryCategory.MULTI_TABLE,
                    "Revenue per product category for orders placed in a date window, joining order_items, products, and orders.",
                    "SELECT p.category, SUM(oi.quantity * oi.unit_price) AS revenue "
                            + "FROM order_items oi "
                            + "JOIN products p ON oi.product_id = p.product_id "
                            + "JOIN orders o ON oi.order_id = o.order_id "
                            + "WHERE o.order_date BETWEEN :startDate AND :endDate "
                            + "GROUP BY p.category ORDER BY revenue DESC"
            )
    );

    private WorkloadCatalog() {
    }

    public static List<WorkloadQuery> all() {
        return QUERIES;
    }

    public static WorkloadQuery byId(String id) {
        return QUERIES.stream()
                .filter(q -> q.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown query id: " + id));
    }

    /**
     * Fixed, representative parameter values that are valid against the
     * generated dataset (customer_id 500 exists whenever numCustomers >= 500,
     * the date window falls inside the generator's 2024-01-01 + 700 day span).
     */
    public static Map<String, Object> defaultParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("customerId", 500);
        params.put("country", "USA");
        params.put("status", "COMPLETED");
        params.put("category", "Electronics");
        params.put("startDate", java.sql.Date.valueOf("2024-06-01"));
        params.put("endDate", java.sql.Date.valueOf("2024-08-01"));
        return params;
    }
}
