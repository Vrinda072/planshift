import type { Experiment } from "../api/types";

/** "orders.customer_id", "orders (a, b)", plus the method when it isn't the default btree. */
export function indexLabel(e: Pick<Experiment, "targetTable" | "targetColumn" | "indexMethod">): string {
  const table = e.targetTable ?? "orders";
  const columns = (e.targetColumn ?? "customer_id").split(",");
  const base = columns.length === 1 ? `${table}.${columns[0]}` : `${table} (${columns.join(", ")})`;
  return e.indexMethod === "hash" ? `${base} · hash` : base;
}
