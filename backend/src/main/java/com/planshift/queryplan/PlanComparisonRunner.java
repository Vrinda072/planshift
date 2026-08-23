package com.planshift.queryplan;

import com.planshift.experiment.IndexChangeService;
import com.planshift.workload.WorkloadCatalog;
import com.planshift.workload.WorkloadQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Temporary CLI trigger proving the plan-comparison pipeline end to end.
 * Usage: mvn spring-boot:run -Dspring-boot.run.arguments="--compare-plan=Q3_ORDER_HISTORY_LOOKUP"
 */
@Component
@Order(5)
public class PlanComparisonRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlanComparisonRunner.class);

    private final IndexChangeService indexChangeService;
    private final ExplainService explainService;
    private final PlanParser planParser;
    private final PlanComparator planComparator;

    public PlanComparisonRunner(IndexChangeService indexChangeService, ExplainService explainService,
                                 PlanParser planParser, PlanComparator planComparator) {
        this.indexChangeService = indexChangeService;
        this.explainService = explainService;
        this.planParser = planParser;
        this.planComparator = planComparator;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("compare-plan")) {
            return;
        }
        WorkloadQuery query = WorkloadCatalog.byId(args.getOptionValues("compare-plan").get(0));
        var params = WorkloadCatalog.defaultParams();

        indexChangeService.dropCustomerIdIndex();
        ExplainCapture baselineCapture = explainService.capture(query, params);
        PlanNode baselinePlan = planParser.parse(baselineCapture.planRoot());

        indexChangeService.addCustomerIdIndex();
        ExplainCapture candidateCapture = explainService.capture(query, params);
        PlanNode candidatePlan = planParser.parse(candidateCapture.planRoot());

        List<PlanDiff> diffs = planComparator.compare(baselinePlan, candidatePlan);

        log.info("BASELINE plan root: {}", describe(baselinePlan));
        log.info("CANDIDATE plan root: {}", describe(candidatePlan));
        log.info("Diffs: {}", diffs);
        log.info("Summary: {}", planComparator.summarize(diffs));
    }

    private String describe(PlanNode node) {
        StringBuilder sb = new StringBuilder(node.nodeType());
        if (node.relationName() != null) {
            sb.append(" on ").append(node.relationName());
        }
        if (!node.children().isEmpty()) {
            sb.append(" -> [");
            for (PlanNode child : node.children()) {
                sb.append(describe(child)).append(" ");
            }
            sb.append("]");
        }
        return sb.toString();
    }
}
