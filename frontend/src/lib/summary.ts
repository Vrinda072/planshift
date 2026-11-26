import type { Experiment, ExperimentQueryResult, PlanNode } from "../api/types";
import { formatBytes } from "../format";
import { isControl } from "./index";
import { outcome, type Outcome } from "./outcome";
import { strategyChange } from "./plan";
import { evidenceNote, noOverlap } from "./timing";
import { verdict } from "./verdict";

export type Tone = Outcome | "control";

function counts(results: ExperimentQueryResult[]) {
  const count = (o: Outcome) => results.filter((r) => outcome(r) === o).length;
  return { faster: count("faster"), slower: count("slower"), unsure: count("unsure"), unchecked: count("unchecked") };
}

/** The direction a finished run is allowed to claim, from the per-query outcomes, never the raw percentage. */
export function tone(experiment: Experiment): Tone {
  if (isControl(experiment)) return "control";
  const { faster, slower, unsure, unchecked } = counts(experiment.queryResults);
  if (faster > 0 && slower === 0) return "faster";
  if (slower > 0 && faster === 0) return "slower";
  if (faster > 0) return "same";
  return unchecked > 0 ? "unchecked" : unsure > 0 ? "unsure" : "same";
}

function parsePlan(json: string): PlanNode | null {
  try {
    const node = JSON.parse(json) as PlanNode;
    return typeof node?.nodeType === "string" ? node : null;
  } catch {
    return null;
  }
}

/** Names PlanShift has given the indexes it creates for a run, now and in earlier versions. */
const TESTED_INDEX = /^(planshift_exp_\d+|idx_planshift_.+|idx_orders_customer_id)$/;

function usesTestedIndex(node: PlanNode | null): boolean {
  if (!node) return false;
  return (!!node.indexName && TESTED_INDEX.test(node.indexName)) || (node.children ?? []).some(usesTestedIndex);
}

/** The index may only be credited with a change if the plan that ran with it actually used it. */
export function creditsIndex(result: ExperimentQueryResult): boolean {
  return usesTestedIndex(parsePlan(result.candidatePlanJson));
}

export function headline(experiment: Experiment): string {
  if (isControl(experiment)) return "A/A check: nothing was changed";
  const results = experiment.queryResults;
  const { faster, slower, unsure, unchecked } = counts(results);
  if (results.length === 1) {
    const direction = faster ? "faster" : slower ? "slower" : null;
    if (direction) {
      return creditsIndex(results[0])
        ? `Your index made this query ${direction}`
        : `This query ran ${direction} with the index`;
    }
    if (unchecked) return "This older result wasn't timing-checked";
    return unsure ? "Too few runs to tell" : "No real difference with this index";
  }
  if (faster && slower) return `${faster} of ${results.length} queries ran faster with the index, ${slower} slower`;
  if (faster || slower) {
    const direction = faster ? "faster" : "slower";
    const moved = results.filter((r) => outcome(r) === direction);
    return moved.every(creditsIndex)
      ? `Your index made ${moved.length} of ${results.length} queries ${direction}`
      : `${moved.length} of ${results.length} queries ran ${direction} with the index`;
  }
  if (unchecked) return `${unchecked} of ${results.length} queries weren't timing-checked`;
  return unsure
    ? `Too few runs to tell for ${unsure} of ${results.length} queries`
    : `No real difference across ${results.length} queries`;
}

/**
 * The third block's title and colour. For one query it states that query's verdict. For several it states the median
 * change and nothing more: the median itself is never timing-checked, so it never takes a verdict's colour.
 */
export function verdictBlock(experiment: Experiment): { tone: Tone; word: string } {
  const direction = tone(experiment);
  if (direction === "control") return { tone: direction, word: "Drift with no change" };
  if (experiment.queryResults.length === 1) {
    const word = {
      faster: "↓ Faster",
      slower: "↑ Slower",
      same: "No real difference",
      unsure: "Too few runs to tell",
      unchecked: "Not checked",
    }[direction];
    return { tone: direction, word };
  }
  return { tone: "same", word: `Median change across ${experiment.queryResults.length} queries` };
}

/** The words under the third block's number: what the number is, and why it does or doesn't count. */
export function verdictCaption(experiment: Experiment): { lead: string; rest: string | null } {
  const overall = experiment.overallPercentageChange ?? 0;
  const lessOrMore = overall < 0 ? "less" : "more";
  const threshold = `${(experiment.thresholdFraction * 100).toFixed(0)}%`;
  const withinThreshold = Math.abs(overall) < experiment.thresholdFraction * 100;

  if (isControl(experiment)) return { lead: "Nothing was changed.", rest: verdict(experiment) };
  if (experiment.queryResults.length > 1) {
    if (experiment.queryResults.every((r) => outcome(r) === "unchecked")) {
      return {
        lead: `${overall < 0 ? "Less" : "More"} time for the median query.`,
        rest: "This older run didn't save individual timings, so no query's change is checked.",
      };
    }
    return {
      lead: `${overall < 0 ? "Less" : "More"} time for the median query.`,
      rest: withinThreshold
        ? `That is inside the ${threshold} threshold.`
        : "The counts show which queries passed the timing check.",
    };
  }
  const result = experiment.queryResults[0];
  switch (outcome(result)) {
    case "same": {
      const small = Math.abs(result.percentageChange) < experiment.thresholdFraction * 100;
      return {
        lead: `The medians differ: ${lessOrMore} time with the index.`,
        rest: small
          ? `It doesn't count, because that is inside the ${threshold} threshold.`
          : "It doesn't count, because the timing check couldn't tell the runs apart.",
      };
    }
    case "unchecked":
      return {
        lead: `${overall < 0 ? "Less" : "More"} time in these runs.`,
        rest: "This older result didn't save its individual runs, so the change can't be checked against variation.",
      };
    case "unsure":
      return {
        lead: `${overall < 0 ? "Less" : "More"} time in these runs.`,
        rest: "With this few, the change can't be checked against variation. Run it again with 4 or more.",
      };
    default:
      return {
        lead: `${overall < 0 ? "Less" : "More"} time per run.`,
        rest: trustNote(result, experiment.thresholdFraction),
      };
  }
}

/** How far to trust one query's change. Only claims full separation when the samples really do not overlap. */
export function trustNote(result: ExperimentQueryResult, thresholdFraction: number): string | null {
  const timing = result.timing;
  if (timing && timing.evidence === "CLEAR" && noOverlap(timing)) {
    return Math.max(...timing.candidateSamplesMs) < Math.min(...timing.baselineSamplesMs)
      ? "Every run with the index beat every run without it."
      : "Every run with the index was slower than every run without it.";
  }
  const note = evidenceNote(result, thresholdFraction);
  return note ? `${note}.` : null;
}

const SCAN_IN_WORDS: Record<string, string> = {
  "Seq Scan": "read through the table",
  "Index Scan": "looked rows up through an index",
  "Index Only Scan": "answered from an index alone",
  "Bitmap Heap Scan": "used an index to pick which parts of the table to read",
  "Bitmap Index Scan": "used an index to find matching rows",
};

/** Everything about a plan that could explain a change, with the run's temporary index under one name. */
function shape(node: PlanNode): string {
  const index = node.indexName?.replace(/^planshift_exp_\d+$/, "the tested index") ?? "";
  return `${node.nodeType}|${node.relationName ?? ""}|${index}(${(node.children ?? []).map(shape).join(",")})`;
}

export type PlanStory =
  | { kind: "scan"; before: { words: string; node: string }; after: { words: string; node: string } }
  /** The first scan matches, but something else in the plan does not. */
  | { kind: "other" }
  | { kind: "same" }
  | { kind: "unknown" };

/** What the planner did differently, claiming "the same" only when the whole plan is. */
export function planStory(result: ExperimentQueryResult): PlanStory {
  const before = parsePlan(result.baselinePlanJson);
  const after = parsePlan(result.candidatePlanJson);
  if (!before || !after) return { kind: "unknown" };
  const change = strategyChange(result.baselinePlanJson, result.candidatePlanJson);
  if (change) {
    const say = (node: string) => ({ node, words: SCAN_IN_WORDS[node] ?? `used a ${node}` });
    return { kind: "scan", before: say(change.before), after: say(change.after) };
  }
  return { kind: shape(before) === shape(after) ? "same" : "other" };
}

export function planStorySummary(result: ExperimentQueryResult): string {
  const story = planStory(result);
  if (story.kind === "scan") return `${story.before.node} to ${story.after.node}`;
  if (story.kind === "same") return "Same plan as before";
  if (story.kind === "other") return "Same first scan, other steps changed";
  return "Plans couldn't be compared";
}

export function costNote(experiment: Experiment): string | null {
  return experiment.indexSizeBytes != null ? formatBytes(experiment.indexSizeBytes) : null;
}
