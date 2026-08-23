package com.planshift.datagen;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a real, throwaway PostgreSQL container (Testcontainers) --
 * a fresh instance created for this test run only, entirely separate from
 * the docker-compose dev database.
 */
@SpringBootTest
@Testcontainers
class DataGeneratorIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private DataGeneratorService generatorService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void sameSeedProducesIdenticalDataset() {
        DataGenConfig config = new DataGenConfig(42L, 50, 10, 100, 3);

        generatorService.generate(config);
        String checksumRun1 = checksumOrderItems();
        int orderItemCount1 = jdbc.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class);

        generatorService.generate(config);
        String checksumRun2 = checksumOrderItems();
        int orderItemCount2 = jdbc.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class);

        assertThat(checksumRun1).isEqualTo(checksumRun2);
        assertThat(orderItemCount1).isEqualTo(orderItemCount2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customers", Integer.class)).isEqualTo(50);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(100);
    }

    @Test
    void ordersReferenceOnlyExistingCustomers() {
        generatorService.generate(new DataGenConfig(7L, 20, 5, 40, 2));

        Integer orphanCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders o LEFT JOIN customers c ON o.customer_id = c.customer_id WHERE c.customer_id IS NULL",
                Integer.class);

        assertThat(orphanCount).isZero();
    }

    private String checksumOrderItems() {
        return jdbc.queryForObject(
                "SELECT md5(string_agg(order_item_id::text || product_id::text || quantity::text, ',' ORDER BY order_item_id)) FROM order_items",
                String.class);
    }
}
