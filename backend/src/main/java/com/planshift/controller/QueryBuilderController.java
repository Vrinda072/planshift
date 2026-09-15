package com.planshift.controller;

import com.planshift.experiment.Experiment;
import com.planshift.experiment.ExperimentRepository;
import com.planshift.experiment.IndexExperimentService;
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
        String indexColumn = request.indexColumn() != null ? request.indexColumn() : request.spec().filterColumn();
        if (indexColumn == null) {
            throw new IllegalArgumentException("indexColumn is required (or set a filter column to default to it)");
        }
        return impactPredictionService.predict(request.spec(), indexColumn);
    }

    /** Builds the query, then runs a real baseline-vs-candidate index experiment against it. */
    @PostMapping("/api/query-builder/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Experiment run(@RequestBody QueryBuilderRunRequest request) {
        BuiltQuery built = queryBuilderService.build(request.spec());

        String indexColumn = request.indexColumn() != null ? request.indexColumn() : request.spec().filterColumn();
        if (indexColumn == null) {
            throw new IllegalArgumentException("indexColumn is required (or set a filter column to default to it)");
        }

        int repetitions = request.repetitions() != null ? request.repetitions() : DEFAULT_REPETITIONS;
        double threshold = request.thresholdFraction() != null
                ? request.thresholdFraction() : RegressionDetector.DEFAULT_THRESHOLD_FRACTION;

        long id = experimentService.startAsync(List.of(built.query()), built.params(), repetitions, threshold,
                request.spec().table(), indexColumn, "CUSTOM_QUERY");
        return experimentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Experiment vanished right after creation: " + id));
    }
}
