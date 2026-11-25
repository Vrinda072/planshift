package com.planshift.impact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planshift.experiment.ExperimentRepository;
import com.planshift.experiment.IndexSpec;
import com.planshift.querybuilder.BuiltQuery;
import com.planshift.querybuilder.QueryBuilderService;
import com.planshift.querybuilder.QuerySpec;
import com.planshift.queryplan.PlanNode;
import com.planshift.queryplan.PlanParser;
import com.planshift.schema.SchemaService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * A quick estimate of what an index would do to a query before running the real experiment. Nothing is executed:
 * only plain EXPLAINs and a lookup in past results.
 *
 * It is deliberately modest. If the index's leading column can't serve the query, it says so. Otherwise it takes
 * the median of past runs that were comparable (same kind of access and filter, similar selectivity), and with
 * fewer than {@value #MIN_NEIGHBORS} of those it falls back to a rule of thumb marked as low confidence.
 */
@Service
public class ImpactPredictionService {

    private static final int MIN_NEIGHBORS = 3;
    /** Neighbors must be within this factor of 10^0.5 (about 3x) in selectivity. */
    private static final double LOG_SELECTIVITY_RADIUS = 0.5;
    private static final double SELECTIVITY_FLOOR = 1e-6;

    private final QueryBuilderService queryBuilderService;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;
    private final PlanParser planParser;
    private final SchemaService schemaService;
    private final ExperimentRepository experimentRepository;

    public ImpactPredictionService(QueryBuilderService queryBuilderService,
                                    NamedParameterJdbcTemplate namedJdbc,
                                    ObjectMapper objectMapper,
                                    PlanParser planParser,
                                    SchemaService schemaService,
                                    ExperimentRepository experimentRepository) {
        this.queryBuilderService = queryBuilderService;
        this.namedJdbc = namedJdbc;
        this.objectMapper = objectMapper;
        this.planParser = planParser;
        this.schemaService = schemaService;
        this.experimentRepository = experimentRepository;
    }

    public QueryFeatures describe(QuerySpec spec, IndexSpec index) {
        for (String column : index.columns()) {
            if (!schemaService.columnExists(spec.table(), column)) {
                throw new IllegalArgumentException("Unknown column: " + spec.table() + "." + column);
            }
        }
        return new QueryFeatures(QueryFeatures.accessOf(spec, index), QueryFeatures.operatorOf(spec),
                filterSelectivity(spec), QueryFeatures.coversOrder(spec, index));
    }

    public PredictedImpact predict(QuerySpec spec, IndexSpec index) {
        QueryFeatures features = describe(spec, index);
        String scanType = findScanNode(explainWithoutRunning(queryBuilderService.build(spec))).nodeType();

        if (features.access() == QueryFeatures.Access.NONE) {
            return new PredictedImpact(0.0, "structure", "low", 0, features.selectivity(), scanType,
                    "This index starts with \"" + index.columns().get(0) + "\", which this query neither filters "
                            + "on in a way a btree can use nor sorts by, so Postgres is unlikely to use it.");
        }

        List<Double> neighbors = experimentRepository.findComparableChanges(features.access().name(),
                        features.operator().name(), features.coversOrder()).stream()
                .filter(p -> Math.abs(log10(p.selectivity()) - log10(features.selectivity())) <= LOG_SELECTIVITY_RADIUS)
                .map(p -> p.percentageChange())
                .sorted()
                .toList();

        if (neighbors.size() >= MIN_NEIGHBORS) {
            int mid = neighbors.size() / 2;
            double median = neighbors.size() % 2 == 0 ? (neighbors.get(mid - 1) + neighbors.get(mid)) / 2 : neighbors.get(mid);
            return new PredictedImpact(median, "historical", "moderate", neighbors.size(), features.selectivity(),
                    scanType, "Median of " + neighbors.size() + " past runs that used an index the same way, with the "
                    + "same kind of filter and a similar selectivity.");
        }

        double heuristic = features.access() == QueryFeatures.Access.ORDER
                ? -25 : -clamp((1 - features.selectivity()) * 45, 3, 70);
        return new PredictedImpact(heuristic, "heuristic", "low", neighbors.size(), features.selectivity(), scanType,
                String.format(Locale.ROOT, "Rule of thumb only: %d comparable past run(s), and %d are needed. The "
                        + "filter keeps about %.2g%% of the table.", neighbors.size(), MIN_NEIGHBORS,
                        features.selectivity() * 100));
    }

    /**
     * The planner's estimate for the filter alone. Asked as its own query because in the full plan a LIMIT or
     * aggregate hides the number, and under a parallel scan the scan node reports rows per worker.
     */
    private double filterSelectivity(QuerySpec spec) {
        BuiltQuery filterOnly = queryBuilderService.build(new QuerySpec(spec.table(), null, null, null, null,
                spec.filterColumn(), spec.filterOperator(), spec.filterValue(), null, null, null));
        if (spec.filterColumn() == null) {
            return 1.0;
        }
        String sql = filterOnly.query().sql().replaceAll(" LIMIT \\d+$", "");
        long matching = explainRows(sql, filterOnly);
        long total = schemaService.rowEstimate(spec.table());
        return total <= 0 ? 1.0 : clamp((double) matching / total, 0.0, 1.0);
    }

    private long explainRows(String sql, BuiltQuery query) {
        String rawJson = namedJdbc.queryForObject("EXPLAIN (FORMAT JSON) " + sql, query.params(), String.class);
        try {
            return objectMapper.readTree(rawJson).get(0).get("Plan").path("Plan Rows").asLong();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse EXPLAIN JSON output while estimating selectivity", e);
        }
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

    /**
     * The plan root is often a Limit/Sort/Aggregate node wrapping the real
     * scan -- its own "Plan Rows" reflects the LIMIT cap or the aggregate's
     * single output row, not how many rows the filter actually matched. Walk
     * down to the first real scan node (Seq/Index/Bitmap) and use that
     * node to report how the query is read today; falls back to the root if none is found.
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

    private static double log10(double selectivity) {
        return Math.log10(Math.max(selectivity, SELECTIVITY_FLOOR));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
