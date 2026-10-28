package com.planshift.controller;

import com.planshift.experiment.Experiment;
import com.planshift.experiment.ExperimentRepository;
import com.planshift.experiment.IndexExperimentService;
import com.planshift.experiment.IndexSpec;
import com.planshift.experiment.RegressionDetector;
import com.planshift.impact.ImpactPredictionService;
import com.planshift.impact.PredictedImpact;
import com.planshift.querybuilder.BuiltQuery;
import com.planshift.querybuilder.QueryBuilderService;
import com.planshift.querybuilder.QuerySpec;
import com.planshift.workload.WorkloadQuery;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
public class QueryBuilderController {

    private static final int DEFAULT_REPETITIONS = 5;

    private final QueryBuilderService queryBuilderService;
    private final IndexExperimentService experimentService;
    private final ExperimentRepository experimentRepository;
    private final ImpactPredictionService impactPredictionService;

    public QueryBuilderController(QueryBuilderService queryBuilderService,
                                   IndexExperimentService experimentService,
                                   ExperimentRepository experimentRepository,
                                   ImpactPredictionService impactPredictionService) {
        this.queryBuilderService = queryBuilderService;
        this.experimentService = experimentService;
        this.experimentRepository = experimentRepository;
        this.impactPredictionService = impactPredictionService;
    }

    /** Builds and validates the query, returning the generated SQL without running it. */
    @PostMapping("/api/query-builder/preview")
    public WorkloadQuery preview(@RequestBody QuerySpec spec) {
        return queryBuilderService.build(spec).query();
    }

    /**
     * Estimates the impact of adding an index without running the real,
     * slow before/after experiment -- a plain EXPLAIN plus a lookup against
     * this app's own experiment history. See ImpactPredictionService.
     */
    @PostMapping("/api/query-builder/predict-impact")
    public PredictedImpact predictImpact(@RequestBody QueryBuilderRunRequest request) {
        IndexSpec index = indexOf(request);
        // The estimate only looks at the leading column, which is what decides whether the planner can use the index.
        return impactPredictionService.predict(request.spec(), index.columns().get(0));
    }

    /** Builds the query, then runs a real baseline-vs-candidate index experiment against it. */
    @PostMapping("/api/query-builder/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Experiment run(@RequestBody QueryBuilderRunRequest request) {
        BuiltQuery built = queryBuilderService.build(request.spec());

        IndexSpec index = indexOf(request);

        int repetitions = request.repetitions() != null ? request.repetitions() : DEFAULT_REPETITIONS;
        double threshold = request.thresholdFraction() != null
                ? request.thresholdFraction() : RegressionDetector.DEFAULT_THRESHOLD_FRACTION;

        long id = experimentService.startAsync(List.of(built.query()), built.params(), repetitions, threshold,
                request.spec().table(), index, "CUSTOM_QUERY");
        return experimentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Experiment vanished right after creation: " + id));
    }

    private static IndexSpec indexOf(QueryBuilderRunRequest request) {
        List<String> columns = request.indexColumns() != null && !request.indexColumns().isEmpty()
                ? request.indexColumns()
                : request.indexColumn() != null ? List.of(request.indexColumn())
                : request.spec().filterColumn() != null ? List.of(request.spec().filterColumn()) : List.of();
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Pick a column to index (or set a filter column to default to it)");
        }
        return new IndexSpec(columns);
    }
}
