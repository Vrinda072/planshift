package com.planshift.controller;

public record ExperimentRequest(
        Integer repetitions,
        Double thresholdFraction
) {
}
