package com.planshift.datagen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Triggers dataset generation when the app is started with --generate-data.
 *
 * Examples:
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--generate-data"
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--generate-data --scale=full"
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--generate-data --customers=20000 --orders=80000"
 *
 * --scale=full uses the fixed 100k-customer/500k-order preset; anything
 * else starts from the planshift.dataset.* defaults (DATASET_CUSTOMERS etc.
 * in .env). Either way, --customers/--products/--orders/--max-items/--seed
 * override individual fields on top of whichever base was picked, so you
 * can scale a one-off run without touching config at all.
 */
@Component
@Order(1)
public class DataGenRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataGenRunner.class);

    private final DataGeneratorService generatorService;

    @Value("${planshift.dataset.seed}")
    private long defaultSeed;

    @Value("${planshift.dataset.customers}")
    private int defaultCustomers;

    @Value("${planshift.dataset.products}")
    private int defaultProducts;

    @Value("${planshift.dataset.orders}")
    private int defaultOrders;

    @Value("${planshift.dataset.max-items-per-order}")
    private int defaultMaxItemsPerOrder;

    public DataGenRunner(DataGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("generate-data")) {
            return;
        }
        String scale = args.containsOption("scale") ? args.getOptionValues("scale").get(0) : "small";
        DataGenConfig base = "full".equalsIgnoreCase(scale)
                ? DataGenConfig.defaultFull()
                : new DataGenConfig(defaultSeed, defaultCustomers, defaultProducts, defaultOrders, defaultMaxItemsPerOrder);

        DataGenConfig config = base.withOverrides(
                longOption(args, "seed"),
                intOption(args, "customers"),
                intOption(args, "products"),
                intOption(args, "orders"),
                intOption(args, "max-items"));

        log.info("Starting data generation, scale={}, config={}", scale, config);
        DataGeneratorService.GenerationResult result = generatorService.generate(config);
        log.info("Generated {} customers, {} products, {} orders, {} order_items in {} ms",
                result.customers(), result.products(), result.orders(), result.orderItems(), result.elapsedMs());
    }

    private Integer intOption(ApplicationArguments args, String name) {
        return args.containsOption(name) ? Integer.valueOf(args.getOptionValues(name).get(0)) : null;
    }

    private Long longOption(ApplicationArguments args, String name) {
        return args.containsOption(name) ? Long.valueOf(args.getOptionValues(name).get(0)) : null;
    }
}
