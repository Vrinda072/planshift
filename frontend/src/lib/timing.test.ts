import { describe, expect, it } from "vitest";
import type { ExperimentQueryResult, TimingEvidence } from "../api/types";
import { evidenceNote } from "./timing";

function result(percentageChange: number, evidence: TimingEvidence | null): ExperimentQueryResult {
  const stats = { executionTimeMs: 1, sharedHitBlocks: 1, sharedReadBlocks: 0 };
  return {
    percentageChange,
    timing: evidence && {
      baselineSamplesMs: [1], candidateSamplesMs: [1], pValue: 1, evidence,
      baselinePlanStats: stats, candidatePlanStats: stats,
    },
  } as ExperimentQueryResult;
}

describe("evidenceNote", () => {
  it("says nothing about results with no stored samples", () => {
    expect(evidenceNote(result(-50, null), 0.2)).toBeNull();
  });

  it("explains why a large change was not counted", () => {
    expect(evidenceNote(result(-50, "OVERLAPPING"), 0.2)).toBe("Past the threshold, but the runs overlap");
    expect(evidenceNote(result(-5, "OVERLAPPING"), 0.2)).toBe("Runs overlap");
  });
});
