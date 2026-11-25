import type { Experiment } from "../api/types";
import { isControl } from "./index";

const quote = (identifier: string) => `"${identifier.replace(/"/g, '""')}"`;

/**
 * The statement that would create the tested index for good. CONCURRENTLY, so that running it against a database in
 * use doesn't block writes. Null where there's no index to create.
 */
export function createIndexSql(
  e: Pick<Experiment, "experimentType" | "targetTable" | "targetColumn">
): string | null {
  if (isControl(e) || !e.targetColumn) return null;
  const columns = e.targetColumn.split(",").map(quote).join(", ");
  return `CREATE INDEX CONCURRENTLY ON ${quote(e.targetTable ?? "orders")} (${columns});`;
}
