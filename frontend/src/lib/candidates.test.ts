import { describe, expect, it } from "vitest";
import type { Experiment } from "../api/types";
import { SAMPLE_QUERY, bestCandidate, candidateIndexes } from "./candidates";

function run(id: number, status: string, percentageChange: number) {
  return { experimentId: id, queryResults: [{ status, percentageChange }] } as unknown as Experiment;
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
