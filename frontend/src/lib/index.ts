import type { Experiment } from "../api/types";

type Target = Pick<Experiment, "targetTable" | "targetColumn"> & Partial<Pick<Experiment, "experimentType">>;

/** An A/A run: the full procedure with nothing changed between the two sides. */
export function isControl(e: Partial<Pick<Experiment, "experimentType">>): boolean {
  return e.experimentType === "AA_CONTROL";
}

export function typeLabel(e: Pick<Experiment, "experimentType">): string {
  if (isControl(e)) return "A/A check";
  return e.experimentType === "CUSTOM_QUERY" ? "Custom query" : "Built-in workload";
}

/** "orders.customer_id" for one column, "orders (a, b)" for a composite index. */
export function indexLabel(e: Target): string {
  const table = e.targetTable ?? "orders";
  if (isControl(e)) return `${table}, no change`;
  const columns = (e.targetColumn ?? "customer_id").split(",");
  return columns.length === 1 ? `${table}.${columns[0]}` : `${table} (${columns.join(", ")})`;
}
