import type { ReactNode } from "react";
import type { Experiment } from "../api/types";
import { formatChangeText } from "../format";
import { verdict } from "../lib/verdict";
import { AnimatedNumber } from "./AnimatedNumber";
import { Dumbbell } from "./Dumbbell";

/**
 * The one dark surface in the app: a finished run, led by its headline number,
 * the sentence that explains it, and how the queries split.
 */
export function Hero({ experiment, eyebrow, aside }: { experiment: Experiment; eyebrow: ReactNode; aside?: ReactNode }) {
  const results = experiment.queryResults;
  const overall = experiment.overallPercentageChange;
  if (overall === null) return null;

  const improved = results.filter((r) => r.status === "IMPROVED").length;
  const regressed = results.filter((r) => r.status === "REGRESSED").length;
  const unchanged = results.length - improved - regressed;
  const tone = overall < 0 ? "good" : overall > 0 ? "bad" : "flat";

  return (
    <section className="hero">
      <div className="hero-glow" aria-hidden="true" />
      <div className="hero-top">
        <span className="hero-eyebrow">{eyebrow}</span>
        {aside}
      </div>

      <div className="hero-main">
        <div>
          <div className={`hero-figure ${tone}`}>
            <AnimatedNumber value={overall} format={formatChangeText} />
          </div>
          <div className="hero-caption">
            median change across {results.length} {results.length === 1 ? "query" : "queries"}
          </div>
        </div>

        <div className="hero-copy">
          <p className="hero-sentence">{verdict(experiment)}</p>
          <div
            className="hero-split"
            role="img"
            aria-label={`${improved} improved, ${unchanged} unchanged, ${regressed} regressed`}
          >
            {improved > 0 && <i className="good" style={{ flexGrow: improved }} />}
            {unchanged > 0 && <i className="flat" style={{ flexGrow: unchanged }} />}
            {regressed > 0 && <i className="bad" style={{ flexGrow: regressed }} />}
          </div>
          <div className="hero-legend">
            <span><b className="tnum">{improved}</b> improved</span>
            <span><b className="tnum">{unchanged}</b> unchanged</span>
            <span><b className="tnum">{regressed}</b> regressed</span>
          </div>
        </div>
      </div>

      <div className="hero-rows">
        <Dumbbell results={results} />
      </div>
    </section>
  );
}
