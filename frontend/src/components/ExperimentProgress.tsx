import type { ExperimentPhase } from "../api/types";
import { PHASE_EXPLANATION, PHASE_LABEL, PHASE_ORDER } from "../phases";

export function ExperimentProgress({ currentPhase }: { currentPhase: ExperimentPhase | null }) {
  const phase = currentPhase ?? "PENDING";
  const currentIndex = PHASE_ORDER.indexOf(phase);

  return (
    <div className="card">
      <div className="category-tag">Running experiment</div>
      <h2 style={{ marginTop: 6 }}>{PHASE_LABEL[phase]}</h2>
      <p style={{ color: "var(--text-secondary)", marginTop: 0 }}>{PHASE_EXPLANATION[phase]}</p>

      <div className="progress-steps">
        {PHASE_ORDER.filter((p) => p !== "DONE").map((p, i) => {
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
  );
}
