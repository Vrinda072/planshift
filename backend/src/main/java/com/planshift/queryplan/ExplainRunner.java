package com.planshift.queryplan;

import com.planshift.workload.WorkloadCatalog;
import com.planshift.workload.WorkloadQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Temporary CLI trigger for the EXPLAIN capture pipeline, ahead of the REST API.
 * Usage: mvn spring-boot:run -Dspring-boot.run.arguments="--explain-query=Q3_ORDER_HISTORY_LOOKUP"
 */
@Component
@Order(3)
public class ExplainRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExplainRunner.class);

    private final ExplainService explainService;

    public ExplainRunner(ExplainService explainService) {
        this.explainService = explainService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("explain-query")) {
            return;
        }
        String queryId = args.getOptionValues("explain-query").get(0);
        WorkloadQuery query = WorkloadCatalog.byId(queryId);

        ExplainCapture capture = explainService.capture(query, WorkloadCatalog.defaultParams());

        log.info("[{}] planning={} ms, execution={} ms, top node={}",
                capture.queryId(), capture.planningTimeMs(), capture.executionTimeMs(),
                capture.planRoot().path("Node Type").asText());
        log.info("[{}] raw plan JSON:\n{}", capture.queryId(), capture.rawJson());
    }
}
