package com.planshift.experiment;

/**
 * Fine-grained progress within a RUNNING experiment, so the UI can show what
 * is genuinely happening right now instead of a generic spinner.
 */
public enum ExperimentPhase {
    PENDING,
    DROPPING_INDEX,
    WARMING_UP_BASELINE,
    MEASURING_BASELINE,
    ADDING_INDEX,
    WARMING_UP_CANDIDATE,
    MEASURING_CANDIDATE,
    COMPARING_RESULTS,
    DONE
}
