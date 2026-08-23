package com.planshift.controller;

import com.planshift.experiment.ExperimentQueryResult;
import com.planshift.experiment.ExperimentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RegressionController {

    private final ExperimentRepository experimentRepository;

    public RegressionController(ExperimentRepository experimentRepository) {
        this.experimentRepository = experimentRepository;
    }

    @GetMapping("/api/regressions")
    public List<ExperimentQueryResult> listRegressions() {
        return experimentRepository.findAllRegressions();
    }
}
