import { api } from "../api/client";
import type { Experiment, QuerySpec } from "../api/types";

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
  thresholdFraction: number
): Promise<string> {
  const started: Experiment[] = [];
  try {
    for (const columns of candidates) {
      started.push(await api.runQueryBuilderExperiment(spec, columns, repetitions, thresholdFraction));
    }
  } catch (e) {
    if (started.length === 0) throw e;
  }
  return `/candidates?ids=${started.map((e) => e.experimentId).join(",")}`;
}

/** The candidate that saved the most, among those counted as an improvement. Null if none was. */
export function bestCandidate(experiments: Experiment[]): Experiment | null {
  const improved = experiments.filter((e) => e.queryResults[0]?.status === "IMPROVED");
  if (improved.length === 0) return null;
  return improved.reduce((best, e) =>
    e.queryResults[0].percentageChange < best.queryResults[0].percentageChange ? e : best
  );
}
