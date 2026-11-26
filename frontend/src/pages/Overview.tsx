import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { Experiment, ExperimentQueryResult } from "../api/types";
import { formatChangeText, formatDate } from "../format";
import { ResultBlocks } from "../components/ResultBlocks";
import { QueryLine } from "../components/QueryLine";
import { outcome } from "../lib/outcome";
import { headline } from "../lib/summary";
import { PageHeader } from "../components/PageHeader";
import { Skeleton } from "../components/Skeleton";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { STATUS_LABEL } from "../lib/status";
import { SAMPLE_QUERY, candidateIndexes, startCandidateRun } from "../lib/candidates";
import { indexLabel, isControl, typeLabel } from "../lib/index";

export function Overview() {
  const [experiments, setExperiments] = useState<Experiment[] | null>(null);
  const [latest, setLatest] = useState<Experiment | null>(null);
  const [regressions, setRegressions] = useState<ExperimentQueryResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sampling, setSampling] = useState(false);
  const navigate = useNavigate();
  useDocumentTitle("Overview");

  useEffect(() => {
    api
      .listExperiments()
      .then((list) => {
        setExperiments(list);
        const last = list.find((e) => e.status === "COMPLETED" && !isControl(e));
        if (last) api.getExperiment(last.experimentId).then(setLatest).catch(() => setLatest(null));
      })
      .catch((e) => setError(e.message));
    api.listRegressions().then((all) => setRegressions(all.filter((r) => outcome(r) === "slower"))).catch(() => setRegressions(null));
  }, []);

  // The running one if there is one, otherwise whatever is next in line.
  const running =
    (experiments ?? []).find((e) => e.status === "RUNNING") ?? (experiments ?? []).find((e) => e.status === "QUEUED");

  const trySample = async () => {
    setSampling(true);
    setError(null);
    try {
      navigate(await startCandidateRun(SAMPLE_QUERY, candidateIndexes(SAMPLE_QUERY), 5, 0.2));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSampling(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="Overview"
        sub={latest ? "What your latest index experiment measured." : experiments === null ? " " : "No completed experiments yet. Run one to see what an index does to your queries."}
        actions={
          <button
            className="btn"
            onClick={trySample}
            disabled={sampling}
            title="Find one customer's latest orders, and try four indexes for it"
          >
            {sampling ? "Starting..." : "Try a sample experiment"}
          </button>
        }
      />

      {error && <div className="error-banner">{error}</div>}

      {running && (
        <div className="notice">
          <span className={`status ${running.status.toLowerCase()}`}>
            Experiment #{running.experimentId} is {STATUS_LABEL[running.status].toLowerCase()}
          </span>
          <Link to={`/experiments/${running.experimentId}`}>Watch it</Link>
        </div>
      )}

      {latest && latest.overallPercentageChange !== null && (
        <>
          <div className="latest-head">
            <h2>{headline(latest)}</h2>
            <p>
              Latest run, #{latest.experimentId} on {indexLabel(latest)}, {formatDate(latest.createdAt)}.{" "}
              <Link to={`/experiments/${latest.experimentId}`}>Open result</Link>
            </p>
          </div>
          <QueryLine experiment={latest} />
          <ResultBlocks experiment={latest} />
        </>
      )}

      <div className="grid grid-2-1">
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
              <table className="stack">
                <thead>
                  <tr>
                    <th>Experiment</th>
                    <th>Index on</th>
                    <th>Ran</th>
                    <th>Status</th>
                    <th className="num">Median change</th>
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
                    <td data-label="Experiment">
                      <span>
                        <span className="row-id">#{exp.experimentId}</span>{" "}
                        <span className="cell-main">{typeLabel(exp)}</span>
                      </span>
                    </td>
                      <td className="sub" data-label="Index on">{indexLabel(exp)}</td>
                      <td className="sub" data-label="Ran">{formatDate(exp.createdAt)}</td>
                      <td data-label="Status">
                        <span className={`status ${exp.status.toLowerCase()}`}>
                          {STATUS_LABEL[exp.status]}
                        </span>
                      </td>
                      <td className="num" data-label="Median change">
                        {exp.status === "COMPLETED" && exp.overallPercentageChange !== null ? (
                          <span className="change-value">
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
    </div>
  );
}
