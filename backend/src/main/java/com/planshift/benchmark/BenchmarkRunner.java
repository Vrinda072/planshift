package com.planshift.benchmark;

import com.planshift.workload.WorkloadCatalog;
import com.planshift.workload.WorkloadQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Temporary CLI entry point for exercising the benchmark pipeline before the
 * REST API (Task 10) exists. Usage:
 * mvn spring-boot:run -Dspring-boot.run.arguments="--run-query=all"
 * mvn spring-boot:run -Dspring-boot.run.arguments="--run-query=Q1_SELECTIVE_CUSTOMER_LOOKUP"
 */
@Component
@Order(2)
public class BenchmarkRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    private final BenchmarkService benchmarkService;

    public BenchmarkRunner(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("run-query")) {
            return;
        }
        String target = args.getOptionValues("run-query").get(0);
        Map<String, Object> params = WorkloadCatalog.defaultParams();

        List<WorkloadQuery> toRun = "all".equalsIgnoreCase(target)
                ? WorkloadCatalog.all()
                : List.of(WorkloadCatalog.byId(target));

        for (WorkloadQuery query : toRun) {
            BenchmarkResult result = benchmarkService.executeTimed(query, params);
            log.info("[{}] {} -> {} rows in {} ms",
                    result.queryId(), query.name(), result.rowsReturned(),
                    String.format("%.3f", result.executionTimeMs()));
        }
    }
}
