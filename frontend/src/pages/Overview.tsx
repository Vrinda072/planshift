import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { Experiment } from "../api/types";
import { changeClassName, formatChangeText, formatDate } from "../format";
import { AnimatedNumber } from "../components/AnimatedNumber";
import { ExplainerPanel } from "../components/ExplainerPanel";
import { IconArrowRight } from "../components/Icons";
import { PageHeader } from "../components/PageHeader";
import { RunBars } from "../components/RunBars";
import { Skeleton } from "../components/Skeleton";

const TREND_RUNS = 12;

export function Overview() {
  const [experiments, setExperiments] = useState<Experiment[] | null>(null);
  const [regressionCount, setRegressionCount] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    api.listExperiments().then(setExperiments).catch((e) => setError(e.message));
    api.listRegressions().then((r) => setRegressionCount(r.length)).catch(() => setRegressionCount(null));
  }, []);

  const completed = useMemo(
    () => (experiments ?? []).filter((e) => e.status === "COMPLETED" && e.overallPercentageChange !== null),
    [experiments]
  );

  const stats = useMemo(() => {
    if (completed.length === 0) return null;
    const changes = completed.map((e) => e.overallPercentageChange as number);
    return {
      best: Math.min(...changes),
      avg: changes.reduce((a, b) => a + b, 0) / changes.length,
      trend: [...completed]
        .sort((a, b) => a.experimentId - b.experimentId)
        .slice(-TREND_RUNS)
        .map((e) => ({ id: e.experimentId, value: e.overallPercentageChange as number })),
    };
  }, [completed]);

  return (
    <div>
      <PageHeader
        title="Overview"
        sub="Measure what an index actually does to a query, before and after, on real PostgreSQL."
        actions={
          <button className="btn btn-primary" onClick={() => navigate("/experiments")}>
            New experiment
          </button>
        }
      />

      {error && <div className="error-banner">{error}</div>}

      {stats && (
        <div className="strip">
          <div className="strip-cell">
            <div className="strip-label">Experiments</div>
            <div className="strip-value">
              <AnimatedNumber value={completed.length} format={(n) => Math.round(n).toString()} />
            </div>
            <div className="strip-hint">completed runs</div>
          </div>
          <div className="strip-cell">
            <div className="strip-label">Best result</div>
            <div className="strip-value good">
              <AnimatedNumber value={stats.best} format={formatChangeText} />
            </div>
            <div className="strip-hint">largest single improvement</div>
          </div>
          <div className="strip-cell">
            <div className="strip-label">Average change</div>
            <div className={`strip-value ${stats.avg <= 0 ? "good" : "bad"}`}>
              <AnimatedNumber value={stats.avg} format={formatChangeText} />
            </div>
            <div className="strip-hint">median of queries, averaged over runs</div>
          </div>
          <div className="strip-cell">
            <div className="strip-label">Regressions</div>
            <div className={`strip-value ${regressionCount ? "bad" : ""}`}>
              {regressionCount === null ? "–" : <AnimatedNumber value={regressionCount} format={(n) => Math.round(n).toString()} />}
            </div>
            <div className="strip-hint">flagged across all runs</div>
          </div>
        </div>
      )}

      <div className="grid grid-2-1">
        <section className="panel">
          <div className="panel-head">
            <h2 className="panel-title">Overall change by run</h2>
            <div className="legend">
              <span><i style={{ background: "var(--good)" }} />Faster</span>
              <span><i style={{ background: "var(--bad)" }} />Slower</span>
            </div>
          </div>
          <div className="panel-body">
            {stats && stats.trend.length > 0 ? (
              <RunBars runs={stats.trend} />
            ) : (
              <div className="empty-state" style={{ padding: "48px 0" }}>
                {experiments === null ? "Loading..." : "Run an experiment to see the trend."}
              </div>
            )}
          </div>
        </section>

        <section className="panel">
          <div className="panel-head">
            <h2 className="panel-title">How a run works</h2>
          </div>
          <div className="panel-body">
            <ExplainerPanel />
          </div>
        </section>
      </div>

      <section className="panel">
        <div className="panel-head">
          <h2 className="panel-title">Recent experiments</h2>
          <span className="panel-meta">
            <Link to="/experiments">View all</Link>
          </span>
        </div>

        {!error && experiments === null && <Skeleton rows={4} columns={5} />}
        {experiments && experiments.length === 0 && (
          <div className="empty-state">
            <strong>No experiments yet</strong>
            Start one from the Experiments page.
          </div>
        )}
        {experiments && experiments.length > 0 && (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Experiment</th>
                  <th>Target</th>
                  <th>Ran</th>
                  <th>Status</th>
                  <th className="num">Result</th>
                </tr>
              </thead>
              <tbody>
                {experiments.slice(0, 6).map((exp) => (
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
                    <td>
                      <span className="row-id">#{exp.experimentId}</span>{" "}
                      <span className="cell-main">{exp.experimentType === "CUSTOM_QUERY" ? "Custom query" : "Built-in workload"}</span>
                    </td>
                    <td className="mono sub">
                      {exp.targetTable ?? "orders"}.{exp.targetColumn ?? "customer_id"}
                    </td>
                    <td className="sub">{formatDate(exp.createdAt)}</td>
                    <td>
                      <span className={`status ${exp.status.toLowerCase()}`}>
                        {exp.status === "COMPLETED" ? "Completed" : exp.status === "RUNNING" ? "Running" : "Failed"}
                      </span>
                    </td>
                    <td className="num">
                      {exp.status === "COMPLETED" && exp.overallPercentageChange !== null ? (
                        <span className={`change-value ${changeClassName(exp.overallPercentageChange)}`}>
                          {formatChangeText(exp.overallPercentageChange)}
                        </span>
                      ) : (
                        <span className="sub">–</span>
                      )}
                      <span style={{ display: "inline-block", width: 18, marginLeft: 6, verticalAlign: "middle", color: "var(--ink-3)" }}>
                        <IconArrowRight />
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
