import type { Experiment } from "../api/types";
import { indexLabel, isControl } from "./index";
import { outcome } from "./outcome";

/**
 * The one-sentence finding for a completed run. Every figure is computed from
 * the rows the page shows, never typed separately.
 */
export function verdict(experiment: Experiment): string | null {
  const results = experiment.queryResults;
  if (experiment.status !== "COMPLETED" || experiment.overallPercentageChange === null || results.length === 0) {
    return null;
  }
  const improved = results.filter((r) => outcome(r) === "faster").length;
  const regressed = results.filter((r) => outcome(r) === "slower").length;
  if (isControl(experiment)) {
    const moved = results.length - results.filter((r) => r.status === "UNCHANGED").length;
    const drift = Math.max(...results.map((r) => Math.abs(r.percentageChange))).toFixed(1);
    return `Nothing changed between the two sides, yet the largest per-query difference was ${drift}%. ${moved} of ${results.length} ${
      results.length === 1 ? "query" : "queries"
    } would have been reported as a change.`;
  }
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
