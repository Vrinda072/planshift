import type { ExperimentQueryResult } from "../api/types";
import { formatChangeText, formatMs } from "../format";
import { strategyChange } from "../lib/plan";

/**
 * One row per query: the grey dot is the median time before the index, the
 * coloured dot after it, on one shared log scale (a 1 ms lookup and a 1 s join
 * would otherwise never fit the same axis). The row says what the planner did.
 */
export function Dumbbell({ results }: { results: ExperimentQueryResult[] }) {
  const times = results.flatMap((r) => [r.baselineMedianMs, r.candidateMedianMs]).filter((v) => v > 0);
  const lo = Math.log10(Math.min(...times));
  const hi = Math.log10(Math.max(...times));
  const span = hi - lo || 1;
  const at = (ms: number) => 4 + ((Math.log10(Math.max(ms, 1e-6)) - lo) / span) * 92;

  return (
    <div className="dumbbells">
      {results.map((r, i) => {
        const a = at(r.baselineMedianMs);
        const b = at(r.candidateMedianMs);
        const tone = r.status === "IMPROVED" ? "good" : r.status === "REGRESSED" ? "bad" : "flat";
        const change = strategyChange(r.baselinePlanJson, r.candidatePlanJson);
        return (
          <div className="dumbbell-row" key={r.id} style={{ animationDelay: `${i * 25}ms` }}>
            <div className="dumbbell-name">
              <span className="cell-main">{r.queryName}</span>
              {change && (
                <span className="cell-sub">
                  {change.before} to {change.after}
                </span>
              )}
            </div>
            <div
              className="dumbbell-track"
              title={`${formatMs(r.baselineMedianMs)} before, ${formatMs(r.candidateMedianMs)} after`}
            >
              <span className={`dumbbell-link ${tone}`} style={{ left: `${Math.min(a, b)}%`, width: `${Math.abs(a - b)}%` }} />
              <span className="dumbbell-dot before" style={{ left: `${a}%` }} />
              <span className={`dumbbell-dot after ${tone}`} style={{ left: `${b}%` }} />
            </div>
            <div className="dumbbell-nums">
              <span className="tnum sub">{formatMs(r.baselineMedianMs)}</span>
              <span className="dumbbell-arrow">to</span>
              <span className="tnum">{formatMs(r.candidateMedianMs)}</span>
            </div>
            <span className={`change-value dumbbell-delta change-${r.percentageChange > 0 ? "positive" : r.percentageChange < 0 ? "negative" : "neutral"}`}>
              {formatChangeText(r.percentageChange)}
            </span>
          </div>
        );
      })}
    </div>
  );
}
