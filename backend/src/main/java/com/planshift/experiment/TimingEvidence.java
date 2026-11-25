package com.planshift.experiment;

/** Whether the two sets of timings can be told apart, independently of how big the change in the median is. */
public enum TimingEvidence {
    /** The samples are separated more than shuffling the same timings between the two sides would usually give. */
    CLEAR,
    /** The two sets of timings overlap too much to say they differ. */
    OVERLAPPING,
    /** So few runs that even completely separated samples couldn't reach the cut-off. */
    TOO_FEW_RUNS
}
