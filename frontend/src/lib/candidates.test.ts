import { describe, expect, it } from "vitest";
import type { Experiment } from "../api/types";
import { SAMPLE_QUERY, bestCandidate, candidateFinding, candidateIndexes, leadingCandidates } from "./candidates";

function run(id: number, status: string, percentageChange: number) {
  return { experimentId: id, queryResults: [{ status, percentageChange, timing: { evidence: "CLEAR" } }] } as unknown as Experiment;
}

describe("candidateIndexes", () => {
  it("tries both columns on their own and in both orders", () => {
    expect(candidateIndexes(SAMPLE_QUERY)).toEqual([
      ["customer_id"],
      ["order_date"],
      ["customer_id", "order_date"],
      ["order_date", "customer_id"],
    ]);
  });

  it("ignores ordering under an aggregate and does not repeat a hand-picked index", () => {
    const counting = { ...SAMPLE_QUERY, aggregateFunction: "COUNT" };
    expect(candidateIndexes(counting, ["customer_id"])).toEqual([["customer_id"]]);
  });
});

describe("bestCandidate", () => {
  it("picks the biggest saving that counted as an improvement", () => {
    const best = bestCandidate([run(1, "IMPROVED", -60), run(2, "UNCHANGED", -99), run(3, "IMPROVED", -90)]);
    expect(best?.experimentId).toBe(3);
  });

  it("names no winner when nothing improved", () => {
    expect(bestCandidate([run(1, "UNCHANGED", -5), run(2, "REGRESSED", 40)])).toBeNull();
  });
});

describe("leadingCandidates", () => {
  it("does not rank candidates whose remaining times are within noise of each other", () => {
    const tied = leadingCandidates([run(1, "IMPROVED", -98.7), run(2, "IMPROVED", -98.4), run(3, "IMPROVED", -60)]);
    expect(tied.map((e) => e.experimentId)).toEqual([1, 2]);
  });
});

describe("candidateFinding", () => {
  const done = (status: string, evidence: string) =>
    ({ status: "COMPLETED", queryResults: [{ status, percentageChange: -50, timing: { evidence } }] }) as unknown as Experiment;

  it("does not advise against indexes that had too few runs to judge", () => {
    const finding = candidateFinding([done("IMPROVED", "TOO_FEW_RUNS"), done("IMPROVED", "TOO_FEW_RUNS")]);
    expect(finding).toContain("Too few runs to tell");
    expect(finding).not.toContain("no reason");
  });

  it("does not say all of them made no difference when only some could be judged", () => {
    const finding = candidateFinding([done("UNCHANGED", "OVERLAPPING"), done("IMPROVED", "TOO_FEW_RUNS")]);
    expect(finding).toContain("1 of 2 had too few runs");
    expect(finding).not.toContain("no reason");
  });
});
