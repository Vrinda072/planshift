import type { Experiment } from "../api/types";
import { indexLabel } from "./index";

/**
 * The one-sentence finding for a completed run. Every figure is computed from
 * the rows the page shows, never typed separately.
 */
export function verdict(experiment: Experiment): string | null {
  const results = experiment.queryResults;
  if (experiment.status !== "COMPLETED" || experiment.overallPercentageChange === null || results.length === 0) {
    return null;
  }
  const improved = results.filter((r) => r.status === "IMPROVED").length;
  const regressed = results.filter((r) => r.status === "REGRESSED").length;
  const target = indexLabel(experiment);
  const overall = experiment.overallPercentageChange;
  const noun = results.length === 1 ? "query" : "queries";
  const threshold = (experiment.thresholdFraction * 100).toFixed(0);

  const lead =
    Math.abs(overall) < experiment.thresholdFraction * 100
      ? `An index on ${target} changed the median query by ${Math.abs(overall).toFixed(1)}%, inside the ${threshold}% threshold.`
      : overall < 0
        ? `An index on ${target} made the median query ${Math.abs(overall).toFixed(1)}% faster.`
        : `An index on ${target} made the median query ${overall.toFixed(1)}% slower.`;

  const parts = [`${improved} of ${results.length} ${noun} improved`];
  parts.push(regressed === 0 ? "none regressed" : `${regressed} regressed`);
  return `${lead} ${parts.join(", ")}.`;
}
