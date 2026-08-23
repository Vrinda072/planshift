package com.planshift.experiment;

import com.planshift.benchmark.RepeatedBenchmarkResult;
import com.planshift.workload.QueryCategory;
import com.planshift.workload.WorkloadQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RegressionDetectorTest {

    private final RegressionDetector detector = new RegressionDetector();
    private final WorkloadQuery query = new WorkloadQuery("Q1", "Test query", QueryCategory.SELECTIVE_FILTER, "desc", "SELECT 1");

    private RepeatedBenchmarkResult result(double medianMs) {
        return new RepeatedBenchmarkResult("Q1", List.of(medianMs), medianMs, 1);
    }

    @Test
    void classifiesRegressionWhenCandidateExceedsThreshold() {
        // baseline 100ms, candidate 130ms = +30%, threshold 20% -> REGRESSED
        QueryComparison comparison = detector.compare(query, result(100.0), result(130.0), 0.20);

        assertThat(comparison.status()).isEqualTo(RegressionStatus.REGRESSED);
        assertThat(comparison.percentageChange()).isCloseTo(30.0, within(0.001));
        assertThat(comparison.absoluteChangeMs()).isCloseTo(30.0, within(0.001));
    }

    @Test
    void classifiesImprovementWhenCandidateFasterThanThreshold() {
        // baseline 100ms, candidate 70ms = -30%, threshold 20% -> IMPROVED
        QueryComparison comparison = detector.compare(query, result(100.0), result(70.0), 0.20);

        assertThat(comparison.status()).isEqualTo(RegressionStatus.IMPROVED);
        assertThat(comparison.percentageChange()).isCloseTo(-30.0, within(0.001));
    }

    @Test
    void classifiesUnchangedWithinThreshold() {
        // baseline 100ms, candidate 110ms = +10%, threshold 20% -> UNCHANGED
        QueryComparison comparison = detector.compare(query, result(100.0), result(110.0), 0.20);

        assertThat(comparison.status()).isEqualTo(RegressionStatus.UNCHANGED);
    }

    @Test
    void boundaryAtExactlyThresholdIsNotRegressed() {
        // baseline 100ms, candidate exactly 120ms = +20%, threshold 20%.
        // The comparison is strictly greater-than, so exactly-at-threshold does not count as regressed.
        QueryComparison comparison = detector.compare(query, result(100.0), result(120.0), 0.20);

        assertThat(comparison.status()).isEqualTo(RegressionStatus.UNCHANGED);
    }

    @Test
    void justOverThresholdIsRegressed() {
        QueryComparison comparison = detector.compare(query, result(100.0), result(120.01), 0.20);

        assertThat(comparison.status()).isEqualTo(RegressionStatus.REGRESSED);
    }

    @Test
    void handlesZeroBaselineWithoutDividingByZero() {
        QueryComparison comparison = detector.compare(query, result(0.0), result(5.0), 0.20);

        assertThat(comparison.percentageChange()).isEqualTo(0.0);
    }
}
