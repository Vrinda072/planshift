package com.planshift.experiment;

/** The order the timed runs are taken in. */
public enum MeasurementMode {
    /** Half the runs without the index, then all the runs with it, then the other half without. */
    BLOCKS,
    /**
     * The index is built once and the two sides take turns. A run without it happens inside a transaction that
     * drops the index and is then rolled back, which blocks other readers of the table while it lasts.
     */
    ALTERNATING;

    /** Taking turns, which reported far less noise as change in bench/mode_check.py. */
    public static final MeasurementMode DEFAULT = ALTERNATING;
}
