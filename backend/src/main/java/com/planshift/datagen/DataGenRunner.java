package com.planshift.datagen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Triggers dataset generation when the app is started with --generate-data.
 * Example: mvn spring-boot:run -Dspring-boot.run.arguments="--generate-data --scale=small"
 * Scale is "small" (fast, for development) or "full" (100k customers / 500k orders).
 */
@Component
@Order(1)
public class DataGenRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataGenRunner.class);

    private final DataGeneratorService generatorService;

    public DataGenRunner(DataGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("generate-data")) {
            return;
        }
        String scale = args.containsOption("scale") ? args.getOptionValues("scale").get(0) : "small";
        DataGenConfig config = "full".equalsIgnoreCase(scale)
                ? DataGenConfig.defaultFull()
                : DataGenConfig.defaultSmall();

        log.info("Starting data generation, scale={}, config={}", scale, config);
        DataGeneratorService.GenerationResult result = generatorService.generate(config);
        log.info("Generated {} customers, {} products, {} orders, {} order_items in {} ms",
                result.customers(), result.products(), result.orders(), result.orderItems(), result.elapsedMs());
    }
}
