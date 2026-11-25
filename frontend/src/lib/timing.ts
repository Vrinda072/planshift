import type { ExperimentQueryResult } from "../api/types";
import { formatMs } from "../format";

export function range(samples: number[]): string {
  return `${formatMs(Math.min(...samples))} to ${formatMs(Math.max(...samples))}`;
}

/** One line on whether the timings support the change. Null for results with no stored samples. */
export function evidenceNote(result: ExperimentQueryResult, thresholdFraction: number): string | null {
  const timing = result.timing;
  if (!timing) return null;
  if (timing.evidence === "TOO_FEW_RUNS") return "Too few runs to check against variation";
  if (timing.evidence === "CLEAR") return "Runs are separated";
  return Math.abs(result.percentageChange) > thresholdFraction * 100
    ? "Past the threshold, but the runs overlap"
    : "Runs overlap";
}
