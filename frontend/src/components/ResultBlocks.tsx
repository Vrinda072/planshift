import type { Experiment } from "../api/types";
import { formatMs } from "../format";
import { outcome } from "../lib/outcome";
import { verdictBlock, verdictCaption } from "../lib/summary";
import { clockName } from "../lib/timing";
import { AnimatedNumber } from "./AnimatedNumber";

function Time({ ms }: { ms: number }) {
  const [, value, unit] = /^([\d.]+)(\D+)$/.exec(formatMs(ms)) ?? ["", formatMs(ms), ""];
  return (
    <div className="block-num">
      {value}
      <small>{unit}</small>
    </div>
  );
}

/**
 * The answer, as three large blocks. One query: its time before, after, and the change. A workload: how many queries
 * moved each way, and the median change.
 */
export function ResultBlocks({ experiment }: { experiment: Experiment }) {
  const results = experiment.queryResults;
  const overall = experiment.overallPercentageChange;
  if (overall === null || results.length === 0) return null;
  const { tone: direction, word } = verdictBlock(experiment);
  const single = results.length === 1 ? results[0] : null;
  const faster = results.filter((r) => outcome(r) === "faster").length;
  const slower = results.filter((r) => outcome(r) === "slower").length;
  const unsure = results.filter((r) => outcome(r) === "unsure").length;
  const checked = results.every((r) => r.timing) ? " and passed the timing check" : "";
  const sides = direction === "control" ? ["first half", "second half"] : ["without the index", "with the index"];
  const clock = results[0].timing ? `, ${clockName(results[0].timing)}` : "";
  const caption = verdictCaption(experiment);

  return (
    <section className="blocks" aria-label="Result">
      {single ? (
        <>
          <div className="block block-before">
            <h2>Before</h2>
            <Time ms={single.baselineMedianMs} />
            <p>Median of {experiment.repetitions} runs {sides[0]}{clock}</p>
          </div>
          <div className="block block-after">
            <h2>After</h2>
            <Time ms={single.candidateMedianMs} />
            <p>Median of {experiment.repetitions} runs {sides[1]}{clock}</p>
          </div>
        </>
      ) : (
        <>
          <div className="block block-count">
            <h2>Faster</h2>
            <div className="block-num">{faster}<small>of {results.length}</small></div>
            <p>queries got faster past the threshold{checked}</p>
          </div>
          <div className="block block-count">
            <h2>Slower</h2>
            <div className="block-num">{slower}<small>of {results.length}</small></div>
            <p>
              queries got slower past the threshold{checked}
              {unsure > 0 && `. ${unsure} had too few runs to tell`}
            </p>
          </div>
        </>
      )}
      <div className={`block block-verdict ${direction}`}>
        <h2>{word}</h2>
        <div className="block-num">
          <AnimatedNumber value={overall} format={(v) => `${Math.abs(v).toFixed(1)}%`} />
        </div>
        <p>
          <b>{caption.lead}</b> {caption.rest}
        </p>
      </div>
    </section>
  );
}
