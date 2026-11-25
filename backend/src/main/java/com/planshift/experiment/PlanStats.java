package com.planshift.experiment;

/**
 * What Postgres itself reported for one EXPLAIN (ANALYZE, BUFFERS) run: server-side execution time, which leaves
 * out the driver, the network and building Java rows, and the shared buffers found in cache (hit) or read in.
 */
public record PlanStats(
        double executionTimeMs,
        long sharedHitBlocks,
        long sharedReadBlocks
) {
}
