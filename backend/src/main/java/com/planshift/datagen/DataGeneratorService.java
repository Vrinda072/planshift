package com.planshift.datagen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a synthetic e-commerce dataset. Every value is derived from a single
 * seeded {@link Random}, consumed in a fixed order (customers, then products, then
 * orders/order_items), so the same seed always reproduces the exact same dataset.
 */
@Service
public class DataGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(DataGeneratorService.class);
    private static final int BATCH_SIZE = 2_000;
    private static final LocalDate ORDER_DATE_START = LocalDate.of(2024, 1, 1);
    private static final int ORDER_DATE_SPAN_DAYS = 700;

    private final JdbcTemplate jdbc;

    public DataGeneratorService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public GenerationResult generate(DataGenConfig config) {
        long start = System.currentTimeMillis();
        Random random = new Random(config.seed());

        truncateAll();

        generateCustomers(config.numCustomers(), random);
        List<Product> products = generateProducts(config.numProducts(), random);
        long orderItemCount = generateOrdersAndItems(config, random, products);

        resetSequences(config.numCustomers(), config.numProducts(), config.numOrders());

        long elapsedMs = System.currentTimeMillis() - start;
        log.info("Data generation complete: {} customers, {} products, {} orders, {} order_items in {} ms",
                config.numCustomers(), config.numProducts(), config.numOrders(), orderItemCount, elapsedMs);

        return new GenerationResult(config.numCustomers(), config.numProducts(), config.numOrders(),
                orderItemCount, elapsedMs);
    }

    private void truncateAll() {
        jdbc.execute("TRUNCATE TABLE order_items, orders, products, customers RESTART IDENTITY CASCADE");
    }

    private void generateCustomers(int count, Random random) {
        String sql = "INSERT INTO customers (customer_id, first_name, last_name, email, city, state, country, signup_date) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        for (int i = 1; i <= count; i++) {
            String first = ReferenceData.FIRST_NAMES[random.nextInt(ReferenceData.FIRST_NAMES.length)];
            String last = ReferenceData.LAST_NAMES[random.nextInt(ReferenceData.LAST_NAMES.length)];
            String[] location = ReferenceData.CITY_STATE_COUNTRY[random.nextInt(ReferenceData.CITY_STATE_COUNTRY.length)];
            String email = (first + "." + last + i + "@example.com").toLowerCase();
            LocalDate signup = ORDER_DATE_START.minusDays(random.nextInt(365));

            batch.add(new Object[]{i, first, last, email, location[0], location[1], location[2],
                    java.sql.Date.valueOf(signup)});

            if (batch.size() == BATCH_SIZE) {
                jdbc.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(sql, batch);
        }
    }

    private List<Product> generateProducts(int count, Random random) {
        String sql = "INSERT INTO products (product_id, sku, name, category, price) VALUES (?, ?, ?, ?, ?)";
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        List<Product> products = new ArrayList<>(count);

        for (int i = 1; i <= count; i++) {
            String category = ReferenceData.PRODUCT_CATEGORIES[random.nextInt(ReferenceData.PRODUCT_CATEGORIES.length)];
            String adjective = ReferenceData.PRODUCT_ADJECTIVES[random.nextInt(ReferenceData.PRODUCT_ADJECTIVES.length)];
            String noun = ReferenceData.PRODUCT_NOUNS[random.nextInt(ReferenceData.PRODUCT_NOUNS.length)];
            String name = adjective + " " + noun;
            String sku = "SKU-" + String.format("%06d", i);
            BigDecimal price = BigDecimal.valueOf(5 + random.nextInt(495)).add(BigDecimal.valueOf(random.nextInt(100), 2));

            batch.add(new Object[]{i, sku, name, category, price});
            products.add(new Product(i, price));

            if (batch.size() == BATCH_SIZE) {
                jdbc.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(sql, batch);
        }
        return products;
    }

    private long generateOrdersAndItems(DataGenConfig config, Random random, List<Product> products) {
        String orderSql = "INSERT INTO orders (order_id, customer_id, order_date, status, total_amount) "
                + "VALUES (?, ?, ?, ?, ?)";
        String itemSql = "INSERT INTO order_items (order_item_id, order_id, product_id, quantity, unit_price) "
                + "VALUES (?, ?, ?, ?, ?)";

        List<Object[]> orderBatch = new ArrayList<>(BATCH_SIZE);
        List<Object[]> itemBatch = new ArrayList<>(BATCH_SIZE * 3);
        long orderItemId = 0;

        for (int orderId = 1; orderId <= config.numOrders(); orderId++) {
            int customerId = 1 + random.nextInt(config.numCustomers());
            LocalDateTime orderDate = ORDER_DATE_START.atStartOfDay()
                    .plusDays(random.nextInt(ORDER_DATE_SPAN_DAYS))
                    .plusHours(random.nextInt(24))
                    .plusMinutes(random.nextInt(60));
            String status = ReferenceData.ORDER_STATUSES[random.nextInt(ReferenceData.ORDER_STATUSES.length)];

            int itemCount = 1 + random.nextInt(config.maxItemsPerOrder());
            BigDecimal orderTotal = BigDecimal.ZERO;
            for (int j = 0; j < itemCount; j++) {
                Product product = products.get(random.nextInt(products.size()));
                int quantity = 1 + random.nextInt(5);
                orderItemId++;
                itemBatch.add(new Object[]{orderItemId, orderId, product.id(), quantity, product.price()});
                orderTotal = orderTotal.add(product.price().multiply(BigDecimal.valueOf(quantity)));
            }

            orderBatch.add(new Object[]{orderId, customerId, Timestamp.valueOf(orderDate), status, orderTotal});

            // Orders must be flushed before the items that reference them (FK constraint),
            // so both batches are flushed together in lockstep rather than independently.
            if (orderBatch.size() == BATCH_SIZE) {
                jdbc.batchUpdate(orderSql, orderBatch);
                orderBatch.clear();
                jdbc.batchUpdate(itemSql, itemBatch);
                itemBatch.clear();
            }
        }
        if (!orderBatch.isEmpty()) {
            jdbc.batchUpdate(orderSql, orderBatch);
        }
        if (!itemBatch.isEmpty()) {
            jdbc.batchUpdate(itemSql, itemBatch);
        }
        return orderItemId;
    }

    private void resetSequences(int numCustomers, int numProducts, int numOrders) {
        jdbc.execute("SELECT setval('customers_customer_id_seq', " + numCustomers + ")");
        jdbc.execute("SELECT setval('products_product_id_seq', " + numProducts + ")");
        jdbc.execute("SELECT setval('orders_order_id_seq', " + numOrders + ")");
        jdbc.execute("SELECT setval('order_items_order_item_id_seq', "
                + "(SELECT COALESCE(MAX(order_item_id), 1) FROM order_items))");
    }

    private record Product(int id, BigDecimal price) {
    }

    public record GenerationResult(int customers, int products, int orders, long orderItems, long elapsedMs) {
    }
}
