import type { PlanStats, Timing } from "../api/types";
import { formatMs } from "../format";

function Runs({ label, samples }: { label: string; samples: number[] }) {
  return (
    <div className="samples-row">
      <span className="samples-label">{label}</span>
      <ol className="samples-list" aria-label={`${label}, in the order timed`}>
        {samples.map((ms, i) => (
          <li key={i}>{formatMs(ms)}</li>
        ))}
      </ol>
    </div>
  );
}

function stats(s: PlanStats) {
  return `${formatMs(s.executionTimeMs)} · ${s.sharedHitBlocks.toLocaleString()} pages hit · ${s.sharedReadBlocks.toLocaleString()} read`;
}

/** Every timed run as a number, then what Postgres reported, kept apart on purpose. */
export function TimingDetail({ timing }: { timing: Timing }) {
  return (
    <div className="samples">
      <h3>Every run</h3>
      <p className="samples-title">
        {timing.source === "DATABASE"
          ? "Postgres's own execution time (leaves out the app, driver and network)"
          : "Timed by the app (includes the driver and reading the rows)"}
        {timing.pValue !== null && timing.evidence !== "TOO_FEW_RUNS" && (
          <> · rank-sum p = {timing.pValue < 0.001 ? "<0.001" : timing.pValue.toFixed(3)}</>
        )}
      </p>
      <Runs label="Before" samples={timing.baselineSamplesMs} />
      <Runs label="After" samples={timing.candidateSamplesMs} />
      <h3>From one more EXPLAIN ANALYZE run each</h3>
      <div className="samples-row"><span className="samples-label">Before</span><span>{stats(timing.baselinePlanStats)}</span></div>
      <div className="samples-row"><span className="samples-label">After</span><span>{stats(timing.candidatePlanStats)}</span></div>
    </div>
  );
}
