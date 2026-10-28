import type { ExperimentPhase } from "../api/types";
import { PHASE_EXPLANATION, PHASE_LABEL, PHASE_ORDER } from "../phases";

export function ExperimentProgress({ currentPhase }: { currentPhase: ExperimentPhase | null }) {
  const phase = currentPhase ?? "PENDING";
  const currentIndex = PHASE_ORDER.indexOf(phase);
  const steps = PHASE_ORDER.filter((p) => p !== "DONE");

  return (
    <section className="panel">
      <div className="panel-head">
        <h2 className="panel-title">
          <span className="status running">{PHASE_LABEL[phase]}</span>
        </h2>
        <span className="panel-meta mono">
          step {Math.min(currentIndex + 1, steps.length)} of {steps.length}
        </span>
      </div>
      <div className="panel-body">
        <p className="panel-note">{PHASE_EXPLANATION[phase]}</p>
        <div className="progress-steps">
          {steps.map((p, i) => {
            const state = i < currentIndex ? "done" : i === currentIndex ? "active" : "pending";
            return (
              <div className={`progress-step ${state}`} key={p}>
                <div className="progress-step-dot" />
                <div className="progress-step-label">{PHASE_LABEL[p]}</div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
