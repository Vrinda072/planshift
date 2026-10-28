import type { PlanNode } from "../api/types";

function firstScan(node: PlanNode): PlanNode | null {
  if (node.nodeType.includes("Scan")) return node;
  for (const child of node.children) {
    const found = firstScan(child);
    if (found) return found;
  }
  return null;
}

function scanType(planJson: string): string | null {
  try {
    return firstScan(JSON.parse(planJson) as PlanNode)?.nodeType ?? null;
  } catch {
    return null;
  }
}

export interface StrategyChange {
  before: string;
  after: string;
}

/** The scan strategy before and after, only when the planner actually switched. */
export function strategyChange(baselinePlanJson: string, candidatePlanJson: string): StrategyChange | null {
  const before = scanType(baselinePlanJson);
  const after = scanType(candidatePlanJson);
  if (!before || !after || before === after) return null;
  return { before, after };
}
