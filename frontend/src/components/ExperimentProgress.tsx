import type { ExperimentPhase, MeasurementMode } from "../api/types";
import { PHASE_EXPLANATION, PHASE_LABEL, phasesFor } from "../phases";

export function ExperimentProgress({
  currentPhase,
  measurement,
}: {
  currentPhase: ExperimentPhase | null;
  measurement?: MeasurementMode;
}) {
  const phase = currentPhase ?? "PENDING";
  const order = phasesFor(measurement);
  const currentIndex = order.indexOf(phase);
  const steps = order.filter((p) => p !== "DONE");

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
