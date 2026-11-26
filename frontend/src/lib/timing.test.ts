import { describe, expect, it } from "vitest";
import type { ExperimentQueryResult, TimingEvidence } from "../api/types";
import { evidenceNote } from "./timing";

function result(percentageChange: number, evidence: TimingEvidence | null, before = [1], after = [1]): ExperimentQueryResult {
  const stats = { executionTimeMs: 1, sharedHitBlocks: 1, sharedReadBlocks: 0 };
  return {
    percentageChange,
    timing: evidence && {
      baselineSamplesMs: before, candidateSamplesMs: after, pValue: 1, evidence,
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

  it("only says runs are separated when none of them cross", () => {
    expect(evidenceNote(result(-50, "CLEAR", [5, 6], [1, 2]), 0.2)).toBe("Runs are separated");
    expect(evidenceNote(result(50, "CLEAR", [1, 2], [3, 4]), 0.2)).toBe("Runs are separated");
    expect(evidenceNote(result(-50, "CLEAR", [5, 6, 1.5], [1, 2, 1]), 0.2))
      .toBe("Passed the timing check (p < 0.05); some runs overlap");
  });
});
