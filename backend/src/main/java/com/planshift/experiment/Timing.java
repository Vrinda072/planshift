package com.planshift.experiment;

import com.planshift.benchmark.TimingSource;

import java.util.List;

/** The measurements behind one query's two medians. Null on results recorded before samples were kept. */
public record Timing(
        List<Double> baselineSamplesMs,
        List<Double> candidateSamplesMs,
        Double pValue,
        TimingEvidence evidence,
        PlanStats baselinePlanStats,
        PlanStats candidatePlanStats,
        TimingSource source
) {
}
