import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { Experiment } from "../api/types";
import { changeClassName, formatChangeText, formatDate } from "../format";
import { ExplainerPanel } from "../components/ExplainerPanel";
import { Skeleton } from "../components/Skeleton";
import { StatCard } from "../components/StatCard";
import { AnimatedNumber } from "../components/AnimatedNumber";
import { TrendSparkline } from "../components/TrendSparkline";

export function Overview() {
  const [experiments, setExperiments] = useState<Experiment[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    api
      .listExperiments()
      .then(setExperiments)
      .catch((e) => setError(e.message));
  }, []);

  const completed = useMemo(
    () => (experiments ?? []).filter((e) => e.status === "COMPLETED" && e.overallPercentageChange !== null),
    [experiments]
  );

  const stats = useMemo(() => {
    if (completed.length === 0) return null;
    const changes = completed.map((e) => e.overallPercentageChange as number);
    const best = Math.min(...changes);
    const regressedCount = completed.reduce(
      (n, e) => n + e.queryResults.filter((r) => r.status === "REGRESSED").length,
      0
    );
    const avg = changes.reduce((a, b) => a + b, 0) / changes.length;
    // Oldest-to-newest for the sparkline, most recent 12 runs.
    const trend = [...completed]
      .sort((a, b) => a.experimentId - b.experimentId)
      .slice(-12)
      .map((e) => e.overallPercentageChange as number);
    return { best, regressedCount, avg, trend };
  }, [completed]);

  return (
    <div>
      <h1>Database performance,</h1>
      <h1 style={{ marginTop: -4 }}>without the guesswork.</h1>
      <p className="subtitle">
        PLANSHIFT runs the same SQL workload against two PostgreSQL configurations, measures real execution
        time, and shows you exactly what changed in the query plan.
      </p>
      <button className="btn btn-primary" onClick={() => navigate("/experiments")}>
        Start Experiment
      </button>

      {stats && (
        <div className="stat-grid">
          <StatCard
            label="Experiments run"
            value={<AnimatedNumber value={completed.length} format={(n) => Math.round(n).toString()} />}
          />
          <StatCard
            label="Best result"
            tone="positive"
            value={<AnimatedNumber value={stats.best} format={(n) => formatChangeText(n)} />}
          />
          <StatCard
            label="Avg. overall change"
            tone={stats.avg <= 0 ? "positive" : "negative"}
            value={<AnimatedNumber value={stats.avg} format={(n) => formatChangeText(n)} />}
          />
          <StatCard
            label="Regressions flagged"
            tone={stats.regressedCount > 0 ? "negative" : "neutral"}
            value={<AnimatedNumber value={stats.regressedCount} format={(n) => Math.round(n).toString()} />}
            hint="across all queries, all runs"
          />
        </div>
      )}

      {stats && stats.trend.length >= 2 && (
        <div className="sparkline-wrap">
          <div className="sparkline-label">Overall change, last {stats.trend.length} runs</div>
          <TrendSparkline values={stats.trend} />
        </div>
      )}

      <ExplainerPanel />

      <div className="section">
        <h2>Recent experiments</h2>
        {error && <div className="error-banner">{error}</div>}
        {!error && experiments === null && <Skeleton rows={4} columns={4} />}
        {experiments && experiments.length === 0 && (
          <div className="empty-state">No experiments yet. Run your first one above.</div>
        )}
        {experiments && experiments.length > 0 && (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Experiment</th>
                  <th>Dataset</th>
                  <th>Ran</th>
                  <th>Result</th>
                </tr>
              </thead>
              <tbody>
                {experiments.slice(0, 8).map((exp) => (
                  <tr
                    key={exp.experimentId}
                    className="clickable"
                    onClick={() => navigate(`/experiments/${exp.experimentId}`)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        navigate(`/experiments/${exp.experimentId}`);
                      }
                    }}
                  >
                    <td>#{exp.experimentId} &middot; {exp.experimentType.replace("_", " ").toLowerCase()}</td>
                    <td className="mono">{exp.datasetCustomers.toLocaleString()} customers</td>
                    <td>{formatDate(exp.createdAt)}</td>
                    <td>
                      {exp.status === "COMPLETED" && exp.overallPercentageChange !== null ? (
                        <span className={`change-value ${changeClassName(exp.overallPercentageChange)}`}>
                          {formatChangeText(exp.overallPercentageChange)}
                        </span>
                      ) : (
                        <span className="loading-line">{exp.status.toLowerCase()}</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
