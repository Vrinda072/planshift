import { api } from "../api/client";
import { outcome } from "./outcome";
import { formatBytes } from "../format";
import { indexLabel } from "./index";
import { isUnfinished } from "./status";
import type { Experiment, MeasurementMode, QuerySpec } from "../api/types";

/**
 * The indexes worth trying for a query: the filter column, the ordering column, both orders of the two, and
 * whatever was picked by hand.
 */
export function candidateIndexes(spec: QuerySpec, chosen: string[] = []): string[][] {
  const filter = spec.filterColumn;
  const order = spec.aggregateFunction ? null : spec.orderByColumn;
  const wanted: string[][] = [];
  if (filter) wanted.push([filter]);
  if (order) wanted.push([order]);
  if (filter && order && filter !== order) wanted.push([filter, order], [order, filter]);
  if (chosen.length > 0) wanted.push(chosen);
  return wanted.filter((c, i) => wanted.findIndex((other) => other.join() === c.join()) === i);
}

/** A query on the built-in data where the candidates give visibly different results. */
export const SAMPLE_QUERY: QuerySpec = {
  table: "orders",
  selectColumns: null,
  aggregateFunction: null,
  aggregateColumn: null,
  groupByColumn: null,
  filterColumn: "customer_id",
  filterOperator: "=",
  filterValue: "500",
  orderByColumn: "order_date",
  orderByDirection: "DESC",
  limit: 20,
};

/**
 * Queues one experiment per candidate and returns the path of the page comparing them. If the queue fills part
 * way, the ones already accepted are still worth showing.
 */
export async function startCandidateRun(
  spec: QuerySpec,
  candidates: string[][],
  repetitions: number,
  thresholdFraction: number,
  measurement: MeasurementMode = "ALTERNATING"
): Promise<string> {
  const started: Experiment[] = [];
  try {
    for (const columns of candidates) {
      started.push(
        await api.runQueryBuilderExperiment(spec, columns, repetitions, thresholdFraction, false, measurement));
    }
  } catch (e) {
    if (started.length === 0) throw e;
  }
  return `/candidates?ids=${started.map((e) => e.experimentId).join(",")}`;
}

/** The candidate that saved the most, among those that count as faster. Null if none does. */
export function bestCandidate(experiments: Experiment[]): Experiment | null {
  const faster = experiments.filter((e) => e.queryResults[0] && outcome(e.queryResults[0]) === "faster");
  if (faster.length === 0) return null;
  return faster.reduce((best, e) =>
    e.queryResults[0].percentageChange < best.queryResults[0].percentageChange ? e : best
  );
}

/**
 * Each candidate has its own baseline, and gaps of a few tens of percent between them can be noise. So any whose
 * remaining time is within this factor of the best one's can't be told apart from it.
 */
const TIE_FACTOR = 1.3;

/** The best candidate and every other that this run can't rank below it. Empty if none counts as faster. */
export function leadingCandidates(experiments: Experiment[]): Experiment[] {
  const best = bestCandidate(experiments);
  if (!best) return [];
  const remaining = (e: Experiment) => 100 + e.queryResults[0].percentageChange;
  return experiments.filter(
    (e) => e.queryResults[0] && outcome(e.queryResults[0]) === "faster" && remaining(e) <= remaining(best) * TIE_FACTOR
  );
}

/** The one-line answer above the comparison. Never advises against an index that wasn't given enough runs to judge. */
export function candidateFinding(experiments: Experiment[]): string {
  const waiting = experiments.filter((e) => isUnfinished(e.status)).length;
  if (waiting > 0) {
    return `Measuring one at a time. ${experiments.length - waiting} of ${experiments.length} done.`;
  }
  const best = bestCandidate(experiments);
  if (!best) {
    const unsure = experiments.filter((e) => e.queryResults[0] && outcome(e.queryResults[0]) === "unsure").length;
    if (experiments.some((e) => e.queryResults[0] && outcome(e.queryResults[0]) === "unchecked")) {
      return "Some of these older results weren't timing-checked, so this can't say whether those indexes help. Run them again.";
    }
    if (unsure === 0) {
      return "None of these indexes made a difference that counts, so there's no reason here to add one.";
    }
    return unsure === experiments.length
      ? "Too few runs to tell whether any of these indexes helps. Run them again with 4 or more."
      : `None of these indexes counts as faster yet, but ${unsure} of ${experiments.length} had too few runs to tell. Run those again with 4 or more before ruling them out.`;
  }
  const size = (e: Experiment) => (e.indexSizeBytes != null ? formatBytes(e.indexSizeBytes) : "unknown size");
  const leaders = leadingCandidates(experiments);
  if (leaders.length > 1) {
    const smallest = leaders.reduce((a, b) => ((b.indexSizeBytes ?? Infinity) < (a.indexSizeBytes ?? Infinity) ? b : a));
    return `${leaders.map(indexLabel).join(" and ")} saved the most, and this run can't tell them apart. The smallest of them is ${indexLabel(smallest)}, at ${size(smallest)}.`;
  }
  const saved = Math.abs(best.queryResults[0].percentageChange).toFixed(1);
  return `${indexLabel(best)} saved the most: ${saved}% less median time, for an index of ${size(best)}.`;
}
