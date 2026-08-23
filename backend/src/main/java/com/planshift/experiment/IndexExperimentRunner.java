package com.planshift.experiment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Temporary CLI trigger for the full baseline-vs-candidate index experiment,
 * ahead of the REST API. Usage:
 * mvn spring-boot:run -Dspring-boot.run.arguments="--run-experiment=index"
 */
@Component
@Order(4)
public class IndexExperimentRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexExperimentRunner.class);

    private final IndexExperimentService experimentService;
    private final ExperimentRepository experimentRepository;

    public IndexExperimentRunner(IndexExperimentService experimentService, ExperimentRepository experimentRepository) {
        this.experimentService = experimentService;
        this.experimentRepository = experimentRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("run-experiment")) {
            return;
        }
        log.info("Running index experiment (baseline: no index, candidate: index on orders.customer_id)...");
        long experimentId = experimentService.runDefault();
        Experiment experiment = experimentRepository.findById(experimentId).orElseThrow();

        log.info("Experiment #{} completed. Overall change: {}%", experimentId, experiment.overallPercentageChange());
        for (ExperimentQueryResult r : experiment.queryResults()) {
            log.info("[{}] {} : {} ms -> {} ms ({}{}%) [{}] :: {}",
                    r.queryId(), r.queryName(),
                    String.format("%.3f", r.baselineMedianMs()),
                    String.format("%.3f", r.candidateMedianMs()),
                    r.percentageChange() >= 0 ? "+" : "",
                    String.format("%.1f", r.percentageChange()),
                    r.status(), r.planDiffSummary());
        }
    }
}
