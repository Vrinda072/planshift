package com.planshift.impact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planshift.experiment.ExperimentRepository;
import com.planshift.experiment.HistoricalResultPoint;
import com.planshift.querybuilder.BuiltQuery;
import com.planshift.querybuilder.QueryBuilderService;
import com.planshift.querybuilder.QuerySpec;
import com.planshift.queryplan.PlanNode;
import com.planshift.queryplan.PlanParser;
import com.planshift.schema.SchemaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Estimates the likely impact of adding an index BEFORE running the real,
 * slow before/after experiment -- using two ingredients, both honest about
 * what they actually are:
 *
 * 1. Postgres's own planner: a plain EXPLAIN (no ANALYZE, nothing executed)
 *    on the built query gives a real row-estimate for the current query,
 *    turned into a selectivity ratio against the table's real row count.
 * 2. This app's own history: past COMPLETED experiments' real, measured
 *    percentage changes, filtered down to ones with a similar selectivity,
 *    averaged as a simple nearest-neighbor estimate.
 *
 * When there isn't enough history yet (fewer than MIN_NEIGHBORS similar
 * past cases), this falls back to a plain rule-of-thumb derived from
 * selectivity alone, and says so -- the same "don't fake the result"
 * discipline the rest of the experiment pipeline follows.
 */
@Service
public class ImpactPredictionService {

    private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final int MIN_NEIGHBORS = 3;
    private static final double SELECTIVITY_NEIGHBOR_RADIUS = 0.15;

    private final QueryBuilderService queryBuilderService;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PlanParser planParser;
    private final SchemaService schemaService;
    private final ExperimentRepository experimentRepository;

    public ImpactPredictionService(QueryBuilderService queryBuilderService,
                                    NamedParameterJdbcTemplate namedJdbc,
                                    JdbcTemplate jdbc,
                                    ObjectMapper objectMapper,
                                    PlanParser planParser,
                                    SchemaService schemaService,
                                    ExperimentRepository experimentRepository) {
        this.queryBuilderService = queryBuilderService;
        this.namedJdbc = namedJdbc;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.planParser = planParser;
        this.schemaService = schemaService;
        this.experimentRepository = experimentRepository;
    }

    public PredictedImpact predict(QuerySpec spec, String indexColumn) {
        if (indexColumn == null || !IDENTIFIER.matcher(indexColumn).matches()
                || !schemaService.columnExists(spec.table(), indexColumn)) {
            throw new IllegalArgumentException("Unknown column: " + spec.table() + "." + indexColumn);
        }

        BuiltQuery built = queryBuilderService.build(spec);
        PlanNode plan = explainWithoutRunning(built);
        PlanNode scanNode = findScanNode(plan);

        long tableRows = countRows(spec.table());
        double selectivity = selectivityOf(scanNode.planRows(), tableRows);
        boolean alreadyIndexed = scanNode.nodeType() != null && scanNode.nodeType().contains("Index");

        List<Double> neighborChanges = findSimilarHistoricalChanges(selectivity);

        if (neighborChanges.size() >= MIN_NEIGHBORS) {
            double predicted = neighborChanges.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            return new PredictedImpact(predicted, "historical", neighborChanges.size(), selectivity, scanNode.nodeType(),
                    "Averaged from " + neighborChanges.size()
                            + " past experiments with a similar selectivity (within " + (int) (SELECTIVITY_NEIGHBOR_RADIUS * 100) + " points).");
        }

        if (alreadyIndexed) {
            return new PredictedImpact(0.0, "heuristic", neighborChanges.size(), selectivity, scanNode.nodeType(),
                    "The planner is already using an index scan for this query -- adding one on \"" + indexColumn
                            + "\" is unlikely to change much.");
        }

        double heuristic = -clamp((1 - selectivity) * 45, 3, 70);
        return new PredictedImpact(heuristic, "heuristic", neighborChanges.size(), selectivity, scanNode.nodeType(),
                "Rule-of-thumb estimate from the planner's own selectivity, not from history -- "
                        + "only " + neighborChanges.size() + " similar past experiment(s) so far, need "
                        + MIN_NEIGHBORS + " for a data-backed prediction.");
    }

    private PlanNode explainWithoutRunning(BuiltQuery built) {
        String explainSql = "EXPLAIN (FORMAT JSON) " + built.query().sql();
        String rawJson = namedJdbc.queryForObject(explainSql, built.params(), String.class);
        try {
            JsonNode resultArray = objectMapper.readTree(rawJson);
            JsonNode planRoot = resultArray.get(0).get("Plan");
            return planParser.parse(planRoot);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse EXPLAIN JSON output while predicting impact", e);
        }
    }

    private List<Double> findSimilarHistoricalChanges(double selectivity) {
        List<Double> matches = new ArrayList<>();
        for (HistoricalResultPoint point : experimentRepository.findCompletedResultsWithPlans()) {
            Double historicalSelectivity = selectivityOfHistoricalPoint(point);
            if (historicalSelectivity != null && Math.abs(historicalSelectivity - selectivity) <= SELECTIVITY_NEIGHBOR_RADIUS) {
                matches.add(point.percentageChange());
            }
        }
        return matches;
    }

    /** Best-effort: reuses the current row count for that table as a stand-in for its size back when that experiment ran. */
    private Double selectivityOfHistoricalPoint(HistoricalResultPoint point) {
        try {
            PlanNode historicalPlan = objectMapper.readValue(point.baselinePlanJson(), PlanNode.class);
            long tableRows = countRows(point.targetTable());
            return selectivityOf(findScanNode(historicalPlan).planRows(), tableRows);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The plan root is often a Limit/Sort/Aggregate node wrapping the real
     * scan -- its own "Plan Rows" reflects the LIMIT cap or the aggregate's
     * single output row, not how many rows the filter actually matched. Walk
     * down to the first real scan node (Seq/Index/Bitmap) and use that
     * node's row estimate instead; falls back to the root if none is found.
     */
    private PlanNode findScanNode(PlanNode root) {
        PlanNode found = searchForScanNode(root);
        return found != null ? found : root;
    }

    private PlanNode searchForScanNode(PlanNode node) {
        if (node.nodeType() != null && node.nodeType().contains("Scan")) {
            return node;
        }
        for (PlanNode child : node.children()) {
            PlanNode found = searchForScanNode(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private double selectivityOf(int planRows, long tableRows) {
        if (tableRows <= 0) {
            return 1.0;
        }
        return clamp((double) planRows / (double) tableRows, 0.0, 1.0);
    }

    private long countRows(String table) {
        if (table == null || !IDENTIFIER.matcher(table).matches() || !schemaService.tableExists(table)) {
            return 0;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + quoteIdent(table), Long.class);
        return count == null ? 0 : count;
    }

    private String quoteIdent(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
