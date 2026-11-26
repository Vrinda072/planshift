import type { ExperimentQueryResult, Timing } from "../api/types";
import { formatMs } from "../format";

export function range(samples: number[]): string {
  return `${formatMs(Math.min(...samples))} to ${formatMs(Math.max(...samples))}`;
}

/** True only if every run on one side is faster than every run on the other. */
export function noOverlap(timing: Pick<Timing, "baselineSamplesMs" | "candidateSamplesMs">): boolean {
  const { baselineSamplesMs: before, candidateSamplesMs: after } = timing;
  return Math.max(...after) < Math.min(...before) || Math.min(...after) > Math.max(...before);
}

export function clockName(timing: Pick<Timing, "source">): string {
  return timing.source === "DATABASE" ? "Postgres's execution time" : "timed by the app";
}

/** One line on whether the timings support the change. Null for results with no stored samples. */
export function evidenceNote(result: ExperimentQueryResult, thresholdFraction: number): string | null {
  const timing = result.timing;
  if (!timing) return null;
  if (timing.evidence === "TOO_FEW_RUNS") return "Too few runs to check against variation";
  if (timing.evidence === "CLEAR") {
    // The check can pass while a few runs still cross, so "separated" is only said when none do.
    return noOverlap(timing) ? "Runs are separated" : "Passed the timing check (p < 0.05); some runs overlap";
  }
  return Math.abs(result.percentageChange) > thresholdFraction * 100
    ? "Past the threshold, but the runs overlap"
    : "Runs overlap";
}
