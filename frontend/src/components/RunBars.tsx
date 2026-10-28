import { formatChangeText } from "../format";

export interface Run {
  id: number;
  value: number;
}

/**
 * Overall % change per run, one bar per experiment. Zero line in the middle;
 * above it is slower (a regression), below is faster. Bar height is scaled
 * to the largest absolute change in view.
 */
export function RunBars({ runs }: { runs: Run[] }) {
  const max = Math.max(...runs.map((r) => Math.abs(r.value)), 1);

  return (
    <div>
      <div className="runbars" role="img" aria-label="Overall performance change for each of the most recent experiments">
        {runs.map((r, i) => (
          <div className="runbar" key={r.id} title={`#${r.id}: ${formatChangeText(r.value)}`}>
            <div
              className={`runbar-fill ${r.value > 0 ? "up" : "down"}`}
              style={{ height: `${(Math.abs(r.value) / max) * 46}%`, animationDelay: `${i * 40}ms` }}
            />
          </div>
        ))}
      </div>
      <div className="runbar-axis">
        {runs.map((r) => (
          <span key={r.id}>#{r.id}</span>
        ))}
      </div>
    </div>
  );
}
