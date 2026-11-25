package com.planshift.experiment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MannWhitneyTest {

    @Test
    void completelySeparatedSamplesGetTheSmallestPossiblePValue() {
        // 2 of the C(10,5) = 252 ways to split ten ranks are this extreme
        double p = MannWhitney.pValue(List.of(1.0, 2.0, 3.0, 4.0, 5.0), List.of(6.0, 7.0, 8.0, 9.0, 10.0));
        assertThat(p).isCloseTo(2.0 / 252, within(1e-12));
    }

    @Test
    void matchesAHandWorkedExample() {
        // ranks of the first group {1,2,4}: of C(6,3) = 20 splits, 4 are at least as far from the expected sum
        double p = MannWhitney.pValue(List.of(1.0, 2.0, 4.0), List.of(3.0, 5.0, 6.0));
        assertThat(p).isCloseTo(4.0 / 20, within(1e-12));
    }

    @Test
    void identicalAndInterleavedSamplesAreNotSeparated() {
        assertThat(MannWhitney.pValue(List.of(2.0, 2.0, 2.0, 2.0), List.of(2.0, 2.0, 2.0, 2.0))).isEqualTo(1.0);
        assertThat(MannWhitney.evidence(List.of(1.0, 3.0, 5.0, 7.0, 9.0), List.of(2.0, 4.0, 6.0, 8.0, 10.0)))
                .isEqualTo(TimingEvidence.OVERLAPPING);
    }

    @Test
    void saysSoWhenThereAreTooFewRunsToTell() {
        assertThat(MannWhitney.evidence(List.of(1.0, 2.0, 3.0), List.of(7.0, 8.0, 9.0)))
                .isEqualTo(TimingEvidence.TOO_FEW_RUNS);
        assertThat(MannWhitney.evidence(List.of(1.0, 2.0, 3.0, 4.0), List.of(7.0, 8.0, 9.0, 10.0)))
                .isEqualTo(TimingEvidence.CLEAR);
    }

    @Test
    void handlesTheLargestRunCountAllowed() {
        List<Double> low = java.util.stream.IntStream.range(0, 30).mapToObj(i -> (double) i).toList();
        List<Double> high = java.util.stream.IntStream.range(100, 130).mapToObj(i -> (double) i).toList();
        assertThat(MannWhitney.pValue(low, high)).isBetween(0.0, 1e-10);
    }
}
