import type { PlanStats, Timing } from "../api/types";
import { formatMs } from "../format";

function Strip({ label, samples, max }: { label: string; samples: number[]; max: number }) {
  return (
    <div className="samples-row">
      <span className="samples-label">{label}</span>
      <div className="samples-track" role="img" aria-label={`${label}: ${samples.map(formatMs).join(", ")}`}>
        {samples.map((ms, i) => (
          <span key={i} className="samples-dot" style={{ left: `${(ms / max) * 100}%` }} title={formatMs(ms)} />
        ))}
      </div>
    </div>
  );
}

function stats(s: PlanStats) {
  return `${formatMs(s.executionTimeMs)} · ${s.sharedHitBlocks.toLocaleString()} hit · ${s.sharedReadBlocks.toLocaleString()} read`;
}

/** Every timed run on a shared linear axis from zero, then what Postgres reported, kept apart on purpose. */
export function TimingDetail({ timing }: { timing: Timing }) {
  const max = Math.max(...timing.baselineSamplesMs, ...timing.candidateSamplesMs) || 1;
  return (
    <div className="samples">
      <p className="samples-title">
        {timing.source === "DATABASE"
          ? "Every run, as Postgres's own execution time (leaves out the app, driver and network)"
          : "Every run, timed by the app (includes the driver and reading the rows)"}
        , 0 to {formatMs(max)}
        {timing.pValue !== null && timing.evidence !== "TOO_FEW_RUNS" && (
          <> · rank-sum p = {timing.pValue < 0.001 ? "<0.001" : timing.pValue.toFixed(3)}</>
        )}
      </p>
      <Strip label="Before" samples={timing.baselineSamplesMs} max={max} />
      <Strip label="After" samples={timing.candidateSamplesMs} max={max} />
      <p className="samples-title" style={{ marginTop: 6 }}>
        Separated runs can still come from the machine speeding up or slowing down. An A/A check shows how often that
        happens here.
      </p>
      <p className="samples-title" style={{ marginTop: 12 }}>
        Postgres, from one more EXPLAIN ANALYZE run each (execution time · shared buffers)
      </p>
      <div className="samples-row"><span className="samples-label">Before</span><span className="mono">{stats(timing.baselinePlanStats)}</span></div>
      <div className="samples-row"><span className="samples-label">After</span><span className="mono">{stats(timing.candidatePlanStats)}</span></div>
    </div>
  );
}
