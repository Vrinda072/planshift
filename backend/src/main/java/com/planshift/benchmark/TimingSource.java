package com.planshift.benchmark;

/** Which clock times the repeated runs. */
public enum TimingSource {
    /** Wall clock around the JDBC call: what a caller feels, including the driver, network and reading rows. */
    APP,
    /**
     * The execution time Postgres reports from EXPLAIN (ANALYZE, TIMING OFF). Immune to the application host
     * being slow, which matters on a CPU-throttled server where pauses can dwarf the query itself.
     */
    DATABASE
}
