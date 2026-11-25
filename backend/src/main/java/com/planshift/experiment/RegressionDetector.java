package com.planshift.experiment;

import com.planshift.benchmark.RepeatedBenchmarkResult;
import com.planshift.workload.WorkloadQuery;
import org.springframework.stereotype.Service;

/**
 * Classifies a baseline-vs-candidate comparison as improved, unchanged, or
 * regressed, using a configurable threshold fraction (default 0.20, i.e. 20%).
 *
 * percentage_change = ((candidate - baseline) / baseline) * 100
 *
 * Past the threshold is necessary but not enough: when there are enough runs to check, the two sets of timings
 * must also be distinguishable (see MannWhitney). With too few runs the threshold decides alone, and the result
 * says that it could not be checked.
 *
 * The threshold is a genuine tradeoff, not a scientifically derived constant:
 * a low threshold (e.g. 5%) flags almost any timing wobble as a "regression",
 * drowning real signal in noise; a high threshold (e.g. 50%) misses real
 * regressions that matter in production. 20% is a common starting point for
 * this kind of benchmark and is intentionally configurable per experiment.
 */
@Service
public class RegressionDetector {

    public static final double DEFAULT_THRESHOLD_FRACTION = 0.20;

    public QueryComparison compare(WorkloadQuery query,
                                    RepeatedBenchmarkResult baseline,
                                    RepeatedBenchmarkResult candidate,
                                    double thresholdFraction) {
        double baselineMs = baseline.medianMs();
        double candidateMs = candidate.medianMs();
        double absoluteChangeMs = candidateMs - baselineMs;
        double percentageChange = baselineMs == 0.0 ? 0.0 : (absoluteChangeMs / baselineMs) * 100.0;

        TimingEvidence evidence = MannWhitney.evidence(baseline.executionTimesMs(), candidate.executionTimesMs());
        double pValue = MannWhitney.pValue(baseline.executionTimesMs(), candidate.executionTimesMs());

        RegressionStatus status;
        if (evidence == TimingEvidence.OVERLAPPING) {
            // A big move in the median means little if the individual runs can't be told apart.
            status = RegressionStatus.UNCHANGED;
        } else if (candidateMs > baselineMs * (1 + thresholdFraction)) {
            status = RegressionStatus.REGRESSED;
        } else if (candidateMs < baselineMs * (1 - thresholdFraction)) {
            status = RegressionStatus.IMPROVED;
        } else {
            status = RegressionStatus.UNCHANGED;
        }

        return new QueryComparison(query.id(), query.name(), baselineMs, candidateMs,
                absoluteChangeMs, percentageChange, status,
                pValue, evidence);
    }
}
