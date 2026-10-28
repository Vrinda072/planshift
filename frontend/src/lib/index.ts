import type { Experiment } from "../api/types";

/** "orders.customer_id" for one column, "orders (a, b)" for a composite index. */
export function indexLabel(e: Pick<Experiment, "targetTable" | "targetColumn">): string {
  const table = e.targetTable ?? "orders";
  const columns = (e.targetColumn ?? "customer_id").split(",");
  return columns.length === 1 ? `${table}.${columns[0]}` : `${table} (${columns.join(", ")})`;
}
