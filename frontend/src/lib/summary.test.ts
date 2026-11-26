import { describe, expect, it } from "vitest";
import type { Experiment, ExperimentQueryResult, TimingEvidence } from "../api/types";
import { changeClass, outcome } from "./outcome";
import { headline, planStory, tone, trustNote, verdictBlock, verdictCaption } from "./summary";

const plan = (nodeType: string, indexName: string | null = null, children: unknown[] = []) =>
  JSON.stringify({ nodeType, indexName, relationName: "orders", children });
const indexed = plan("Bitmap Heap Scan", null, [JSON.parse(plan("Bitmap Index Scan", "planshift_exp_7"))]);

function result(
  status: ExperimentQueryResult["status"],
  before = [5],
  after = [1],
  evidence: TimingEvidence = "CLEAR"
): ExperimentQueryResult {
  return {
    status,
    percentageChange: status === "REGRESSED" ? 50 : -50,
    baselinePlanJson: plan("Seq Scan"),
    candidatePlanJson: indexed,
    timing: { evidence, baselineSamplesMs: before, candidateSamplesMs: after },
  } as ExperimentQueryResult;
}

const run = (results: ExperimentQueryResult[], experimentType = "CUSTOM_QUERY", overallPercentageChange = -50) =>
  ({ experimentType, queryResults: results, overallPercentageChange, thresholdFraction: 0.2 }) as Experiment;

describe("outcome", () => {
  it("claims no direction when there were too few runs to check", () => {
    const few = result("IMPROVED", [5], [1], "TOO_FEW_RUNS");
    expect(outcome(few)).toBe("unsure");
    expect(changeClass(few)).toBe("change-neutral");
    expect(headline(run([few]))).toBe("Too few runs to tell");
    expect(verdictBlock(run([few]))).toEqual({ tone: "unsure", word: "Too few runs to tell" });
  });

  it("does not claim there was no difference from too few runs either", () => {
    expect(outcome(result("UNCHANGED", [5], [5], "TOO_FEW_RUNS"))).toBe("unsure");
  });

  it("claims nothing for results that never saved their runs", () => {
    const old: ExperimentQueryResult = { ...result("REGRESSED"), timing: null };
    expect(outcome(old)).toBe("unchecked");
    expect(changeClass(old)).toBe("change-neutral");
    expect(headline(run([old]))).toBe("This older result wasn't timing-checked");
    expect(verdictBlock(run([old]))).toEqual({ tone: "unchecked", word: "Not checked" });
  });

  it("keeps the slower colour for results that count as slower", () => {
    const bigger: ExperimentQueryResult = { ...result("UNCHANGED"), percentageChange: 40 };
    expect(changeClass(bigger)).toBe("change-neutral");
    expect(changeClass(result("REGRESSED"))).toBe("change-positive");
  });
});

describe("headline", () => {
  it("claims a direction only when a query's verdict does", () => {
    expect(headline(run([result("IMPROVED")]))).toBe("Your index made this query faster");
    expect(headline(run([result("UNCHANGED")]))).toBe("No real difference with this index");
    expect(tone(run([result("UNCHANGED")]))).toBe("same");
  });

  it("does not credit an index the plan never used", () => {
    const unused = { ...result("REGRESSED"), candidatePlanJson: plan("Seq Scan") };
    expect(headline(run([unused]))).toBe("This query ran slower with the index");
  });

  it("reports both directions across a workload", () => {
    const mixed = run([result("IMPROVED"), result("REGRESSED"), result("UNCHANGED")]);
    expect(headline(mixed)).toBe("1 of 3 queries ran faster with the index, 1 slower");
  });

  it("never calls a control run faster", () => {
    const control = run([result("IMPROVED")], "AA_CONTROL");
    expect(headline(control)).toBe("A/A check: nothing was changed");
    expect(tone(control)).toBe("control");
  });
});

describe("a workload's median", () => {
  it("never takes a verdict's colour", () => {
    const workload = run([result("IMPROVED"), result("UNCHANGED")], "INDEX_ADD", -40);
    expect(verdictBlock(workload)).toEqual({ tone: "same", word: "Median change across 2 queries" });
    expect(verdictCaption(workload).rest).toBe("The counts show which queries passed the timing check.");
  });
});

describe("verdictCaption", () => {
  it("gives the actual reason a difference doesn't count", () => {
    const overlapping = run([result("UNCHANGED", [5], [1], "OVERLAPPING")]);
    expect(verdictCaption(overlapping).rest).toContain("couldn't tell the runs apart");
    const small = run([{ ...result("UNCHANGED"), percentageChange: -3 }]);
    expect(verdictCaption(small).rest).toContain("inside the 20% threshold");
  });
});

describe("trustNote", () => {
  it("only says every run won when the samples really do not overlap", () => {
    expect(trustNote(result("IMPROVED", [5, 6], [1, 2]), 0.2)).toBe("Every run with the index beat every run without it.");
    expect(trustNote(result("IMPROVED", [5, 6, 1.5], [1, 2, 1]), 0.2))
      .toBe("Passed the timing check (p < 0.05); some runs overlap.");
  });
});

describe("planStory", () => {
  it("puts the scan change in plain words and keeps Postgres's name for it", () => {
    expect(planStory(result("IMPROVED"))).toMatchObject({
      kind: "scan",
      before: { node: "Seq Scan", words: "read through the table" },
    });
  });

  it("says the plan is the same only when all of it is", () => {
    const sorted = (child: string) => plan("Sort", null, [JSON.parse(child)]);
    const base = { ...result("UNCHANGED"), baselinePlanJson: sorted(plan("Index Scan", "old_idx")) };
    expect(planStory({ ...base, candidatePlanJson: sorted(plan("Index Scan", "old_idx")) }).kind).toBe("same");
    expect(planStory({ ...base, candidatePlanJson: sorted(plan("Index Scan", "planshift_exp_3")) }).kind).toBe("other");
    expect(planStory({ ...base, candidatePlanJson: "not json" }).kind).toBe("unknown");
  });
});
