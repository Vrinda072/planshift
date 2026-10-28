import type { ExperimentQueryResult } from "../api/types";

/** Below this, a median is mostly timer and scheduler jitter rather than the query. */
export const NOISE_FLOOR_MS = 1;

/** True when every query in the run was faster than the noise floor both before and after. */
export function belowNoiseFloor(results: ExperimentQueryResult[]): boolean {
  return (
    results.length > 0 &&
    results.every((r) => r.baselineMedianMs < NOISE_FLOOR_MS && r.candidateMedianMs < NOISE_FLOOR_MS)
  );
}
