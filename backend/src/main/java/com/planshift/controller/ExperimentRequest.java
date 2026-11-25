package com.planshift.controller;

public record ExperimentRequest(
        Integer repetitions,
        Double thresholdFraction,
        Boolean control,
        com.planshift.experiment.MeasurementMode measurement
) {
}
