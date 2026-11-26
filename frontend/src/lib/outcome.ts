import type { ExperimentQueryResult } from "../api/types";

/** What one query's result is allowed to claim. */
export type Outcome = "faster" | "slower" | "same" | "unsure";

/**
 * With too few runs to check against variation, the server decides from the threshold alone. That is not enough to
 * claim a direction, or to claim there was no difference, so such a result reads as unsure everywhere.
 */
export function outcome(result: Pick<ExperimentQueryResult, "status" | "timing">): Outcome {
  if (result.timing?.evidence === "TOO_FEW_RUNS") return "unsure";
  if (result.status === "UNCHANGED") return "same";
  return result.status === "IMPROVED" ? "faster" : "slower";
}

export const OUTCOME_LABEL: Record<Outcome, string> = {
  faster: "Faster",
  slower: "Slower",
  same: "No real change",
  unsure: "Too few runs to tell",
};

/** Colour follows the verdict, never the sign of the number: the "slower" colour is only for results that count. */
export function changeClass(result: Pick<ExperimentQueryResult, "status" | "timing">): string {
  const o = outcome(result);
  return o === "faster" ? "change-negative" : o === "slower" ? "change-positive" : "change-neutral";
}
