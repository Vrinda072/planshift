import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { Experiment, ExperimentQueryResult } from "../api/types";
import { changeClassName, formatChangeText, formatDate } from "../format";
import { Hero } from "../components/Hero";
import { PageHeader } from "../components/PageHeader";
import { RunBars } from "../components/RunBars";
import { Skeleton } from "../components/Skeleton";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { indexLabel } from "../lib/index";

const TREND_RUNS = 12;

export function Overview() {
  const [experiments, setExperiments] = useState<Experiment[] | null>(null);
  const [latest, setLatest] = useState<Experiment | null>(null);
  const [regressions, setRegressions] = useState<ExperimentQueryResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  useDocumentTitle("Overview");

  useEffect(() => {
    api
      .listExperiments()
      .then((list) => {
        setExperiments(list);
        const last = list.find((e) => e.status === "COMPLETED");
        if (last) api.getExperiment(last.experimentId).then(setLatest).catch(() => setLatest(null));
      })
      .catch((e) => setError(e.message));
    api.listRegressions().then(setRegressions).catch(() => setRegressions(null));
  }, []);

  const completed = useMemo(
    () => (experiments ?? []).filter((e) => e.status === "COMPLETED" && e.overallPercentageChange !== null),
    [experiments]
  );

  const trend = useMemo(
    () =>
      [...completed]
        .sort((a, b) => a.experimentId - b.experimentId)
        .slice(-TREND_RUNS)
        .map((e) => ({ id: e.experimentId, value: e.overallPercentageChange as number })),
    [completed]
  );

  const best = completed.length ? Math.min(...completed.map((e) => e.overallPercentageChange as number)) : null;
  const running = (experiments ?? []).find((e) => e.status === "RUNNING");

  return (
    <div>
      <PageHeader
        title="Overview"
        sub={latest ? "What your last index experiment did to the workload." : experiments === null ? " " : "No completed experiments yet. Run one to see what an index does to your queries."}
        actions={
          <button className="btn btn-primary" onClick={() => navigate("/experiments")}>
            New experiment
          </button>
        }
      />

      {error && <div className="error-banner">{error}</div>}

      {running && (
        <div className="notice">
          <span className="status running">Experiment #{running.experimentId} is running</span>
          <Link to={`/experiments/${running.experimentId}`}>Watch it</Link>
        </div>
      )}

      {latest && latest.overallPercentageChange !== null && (
        <Hero
          experiment={latest}
          eyebrow={
            <>
              Latest run · #{latest.experimentId} · <span className="mono">{indexLabel(latest)}</span> · {formatDate(latest.createdAt)}
            </>
          }
          aside={<Link to={`/experiments/${latest.experimentId}`} className="hero-link">Open result</Link>}
        />
      )}

      <div className="grid grid-2-1">
        <section className="panel">
          <div className="panel-head">
            <h2 className="panel-title">History</h2>
            <span className="panel-meta">
              {completed.length} {completed.length === 1 ? "run" : "runs"}
              {best !== null && <> · best {formatChangeText(best)}</>}
            </span>
          </div>
          <div className="panel-body">
            {trend.length > 0 ? (
              <>
                <RunBars runs={trend} />
                <div className="legend" style={{ marginTop: 12 }}>
                  <span><i style={{ background: "var(--good)" }} />Faster than baseline</span>
                  <span><i style={{ background: "var(--bad)" }} />Slower</span>
                </div>
              </>
            ) : (
              <div className="empty-state" style={{ padding: "40px 0" }}>{experiments === null ? "Loading..." : "Nothing to chart yet."}</div>
            )}
          </div>
        </section>

        <section className="panel">
          <div className="panel-head">
            <h2 className="panel-title">Slower than before</h2>
            <span className="panel-meta"><Link to="/regressions">All</Link></span>
          </div>
          {regressions === null && <div className="empty-state">Loading...</div>}
          {regressions && regressions.length === 0 && (
            <div className="empty-state" style={{ padding: "32px 18px" }}>No query has regressed.</div>
          )}
          {regressions && regressions.length > 0 && (
            <ul className="list">
              {regressions.slice(0, 5).map((r) => (
                <li key={r.id}>
                  <Link to={`/experiments/${r.experimentId}`} className="list-row">
                    <span>
                      <span className="cell-main">{r.queryName}</span>
                      <span className="cell-sub">Experiment #{r.experimentId}</span>
                    </span>
                    <span className="change-value change-positive">{formatChangeText(r.percentageChange)}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <section className="panel">
        <div className="panel-head">
          <h2 className="panel-title">Recent experiments</h2>
          <span className="panel-meta"><Link to="/experiments">All experiments</Link></span>
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
                  <th>Index on</th>
                  <th>Ran</th>
                  <th>Status</th>
                  <th className="num">Change</th>
                </tr>
              </thead>
              <tbody>
                {experiments.slice(0, 5).map((exp) => (
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
                    <td className="mono sub">{indexLabel(exp)}</td>
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
