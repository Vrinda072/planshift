import type { Experiment } from "../api/types";
import { formatMs } from "../format";
import { isControl } from "../lib/index";
import { outcome } from "../lib/outcome";
import { costNote, planStory } from "../lib/summary";
import { IndexPanel } from "./IndexPanel";

/** Under the blocks of a one-query run: why it changed, what the index costs, and how to keep it. */
export function ResultCards({ experiment, onShowPlan, planOpen }: {
  experiment: Experiment;
  onShowPlan: () => void;
  planOpen: boolean;
}) {
  const result = experiment.queryResults[0];
  const story = planStory(result);
  const direction = outcome(result);
  const size = costNote(experiment);
  const control = isControl(experiment);

  return (
    <div className="cards">
      <section className="card">
        <h2>What changed in the plan</h2>
        {story.kind === "scan" ? (
          <>
            <p>
              Before, Postgres <b>{story.before.words}</b> <span className="plan-term">{story.before.node}</span>
            </p>
            <p>
              After, it <b>{story.after.words}</b> <span className="plan-term">{story.after.node}</span>
            </p>
          </>
        ) : story.kind === "same" ? (
          <p>
            Postgres used the same plan both times
            {!control && (direction === "faster" || direction === "slower") ? ", so the plan doesn't explain the difference." : "."}
          </p>
        ) : story.kind === "other" ? (
          <p>Both plans start with the same kind of scan, but other steps or the index used changed.</p>
        ) : (
          <p>The two plans couldn't be compared.</p>
        )}
        <button className="btn see-why-toggle" onClick={onShowPlan} aria-expanded={planOpen}>
          {planOpen ? "Hide runs and plan" : "See runs and plan"}
        </button>
      </section>

      {!control && (
        <section className="card">
          <h2>What it costs</h2>
          {size ? (
            <>
              <p className="card-figure">{size}</p>
              <p>
                of disk space
                {experiment.indexBuildMs != null && <>, built in {formatMs(experiment.indexBuildMs)}</>}.
              </p>
            </>
          ) : (
            <p>This run didn't record the index's size.</p>
          )}
          <p className="card-note">An index also slows writes. That isn't measured here.</p>
        </section>
      )}

      <IndexPanel experiment={experiment} />
    </div>
  );
}
