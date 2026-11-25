package com.planshift.controller;

import com.planshift.experiment.Experiment;
import com.planshift.experiment.ExperimentRepository;
import com.planshift.experiment.IndexExperimentService;
import com.planshift.experiment.IndexSpec;
import com.planshift.experiment.KeptIndexService;
import com.planshift.experiment.MeasurementMode;
import com.planshift.experiment.RegressionDetector;
import com.planshift.workload.WorkloadCatalog;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
public class ExperimentController {

    private static final int DEFAULT_REPETITIONS = 5;

    private final IndexExperimentService experimentService;
    private final ExperimentRepository experimentRepository;
    private final KeptIndexService keptIndexService;

    public ExperimentController(IndexExperimentService experimentService, ExperimentRepository experimentRepository,
                                KeptIndexService keptIndexService) {
        this.keptIndexService = keptIndexService;
        this.experimentService = experimentService;
        this.experimentRepository = experimentRepository;
    }

    @GetMapping("/api/experiments")
    public List<Experiment> listExperiments() {
        return experimentRepository.findAllSummaries();
    }

    @GetMapping("/api/experiments/{id}")
    public Experiment getExperiment(@PathVariable long id) {
        return experimentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No experiment with id " + id));
    }

    @PostMapping("/api/experiments/{id}/kept-index")
    public Experiment keepIndex(@PathVariable long id) {
        return keptIndexService.keep(id);
    }

    @DeleteMapping("/api/experiments/{id}/kept-index")
    public Experiment removeKeptIndex(@PathVariable long id) {
        return keptIndexService.remove(id);
    }

    @PostMapping("/api/experiments")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Experiment runExperiment(@RequestBody(required = false) ExperimentRequest request) {
        int repetitions = (request != null && request.repetitions() != null) ? request.repetitions() : DEFAULT_REPETITIONS;
        double threshold = (request != null && request.thresholdFraction() != null)
                ? request.thresholdFraction() : RegressionDetector.DEFAULT_THRESHOLD_FRACTION;

        // Returns immediately with the experiment in QUEUED state; the real work
        // happens on a background thread and the client polls GET /api/experiments/{id}
        // for genuine progress (see current_phase), not a simulated spinner.
        MeasurementMode measurement = request != null && request.measurement() != null
                ? request.measurement() : MeasurementMode.DEFAULT;
        long id = request != null && Boolean.TRUE.equals(request.control())
                ? experimentService.startControlAsync(WorkloadCatalog.all(), WorkloadCatalog.defaultParams(),
                        repetitions, threshold, "orders", measurement)
                : experimentService.startAsync(WorkloadCatalog.all(), WorkloadCatalog.defaultParams(),
                        repetitions, threshold, "orders", IndexSpec.of("customer_id"), "INDEX_ADD", measurement);
        return experimentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Experiment vanished right after creation: " + id));
    }
}
