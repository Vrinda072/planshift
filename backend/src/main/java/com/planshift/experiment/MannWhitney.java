package com.planshift.experiment;

import java.util.Arrays;
import java.util.List;

/**
 * Two-sided Mann-Whitney U (rank-sum) test, computed exactly from the permutation distribution of the observed
 * ranks, ties included. It makes no assumption about the shape of the timings, which matters because query times
 * are skewed and have outliers, and sample counts here are small.
 *
 * It does assume the runs are independent. Consecutive runs share cache and machine state, so treat the p-value as
 * optimistic; an A/A run shows how often this procedure reports a difference when there is none.
 */
public final class MannWhitney {

    public static final double ALPHA = 0.05;

    private MannWhitney() {
    }

    public static double pValue(List<Double> first, List<Double> second) {
        int n1 = first.size();
        int n = n1 + second.size();
        if (n1 == 0 || second.isEmpty()) {
            return 1.0;
        }
        double[] all = new double[n];
        for (int i = 0; i < n; i++) {
            all[i] = i < n1 ? first.get(i) : second.get(i - n1);
        }
        double[] sorted = all.clone();
        Arrays.sort(sorted);

        // Ranks are doubled so that tied "mid-ranks" stay whole numbers.
        int[] ranks = new int[n];
        for (int i = 0; i < n; i++) {
            int below = 0;
            while (sorted[below] < all[i]) {
                below++;
            }
            int through = below;
            while (through < n && sorted[through] == all[i]) {
                through++;
            }
            ranks[i] = below + through + 1;
        }

        int observed = 0;
        for (int i = 0; i < n1; i++) {
            observed += ranks[i];
        }
        int maxSum = n * (n + 1);
        // ways[k][s]: number of ways to choose k of the ranks adding up to s
        double[][] ways = new double[n1 + 1][maxSum + 1];
        ways[0][0] = 1;
        for (int rank : ranks) {
            for (int k = n1; k >= 1; k--) {
                for (int s = maxSum; s >= rank; s--) {
                    ways[k][s] += ways[k - 1][s - rank];
                }
            }
        }

        int expected = n1 * (n + 1);
        int distance = Math.abs(observed - expected);
        double extreme = 0;
        double total = 0;
        for (int s = 0; s <= maxSum; s++) {
            total += ways[n1][s];
            if (Math.abs(s - expected) >= distance) {
                extreme += ways[n1][s];
            }
        }
        return Math.min(1.0, extreme / total);
    }

    public static TimingEvidence evidence(List<Double> first, List<Double> second) {
        // The smallest two-sided p-value these sample sizes can produce is 2 / C(n1 + n2, n1).
        double combinations = 1;
        for (int i = 1; i <= first.size(); i++) {
            combinations = combinations * (second.size() + i) / i;
        }
        if (2 / combinations >= ALPHA) {
            return TimingEvidence.TOO_FEW_RUNS;
        }
        return pValue(first, second) < ALPHA ? TimingEvidence.CLEAR : TimingEvidence.OVERLAPPING;
    }
}
