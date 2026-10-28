import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import type { Experiment } from "../api/types";
import { changeClassName, formatChangeText, formatMs } from "../format";
import { PageHeader } from "../components/PageHeader";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { indexLabel } from "../lib/index";

function label(e: Experiment) {
  return `#${e.experimentId} (${indexLabel(e)})`;
}

export function Compare() {
  const [params] = useSearchParams();
  const a = Number(params.get("a"));
  const b = Number(params.get("b"));
  const [pair, setPair] = useState<[Experiment, Experiment] | null>(null);
  const [error, setError] = useState<string | null>(null);
  useDocumentTitle("Compare");

  useEffect(() => {
    if (!a || !b) return;
    Promise.all([api.getExperiment(a), api.getExperiment(b)])
      .then(([x, y]) => setPair([x, y]))
      .catch((e) => setError(e.message));
  }, [a, b]);

  const rows = useMemo(() => {
    if (!pair) return [];
    const [x, y] = pair;
    const byId = new Map(y.queryResults.map((r) => [r.queryId, r]));
    return x.queryResults
      .filter((r) => byId.has(r.queryId))
      .map((r) => ({ left: r, right: byId.get(r.queryId)! }));
  }, [pair]);

  if (!a || !b) {
    return (
      <PageHeader
        title="Compare experiments"
        sub={
          <>
            Pick two runs on the <Link to="/experiments" style={{ color: "var(--accent)" }}>experiments page</Link> and
            choose Compare.
          </>
        }
      />
    );
  }

  if (error) return <div className="error-banner">{error}</div>;
  if (!pair) return <div className="loading-line">Loading...</div>;
  const [left, right] = pair;

  return (
    <div>
      <PageHeader
        title="Compare experiments"
        sub={`${label(left)} against ${label(right)}. Only queries that appear in both runs are listed.`}
      />

      <section className="panel">
        <div className="panel-head">
          <h2 className="panel-title">Overall change</h2>
        </div>
        <div className="compare-headline">
          {[left, right].map((e) => (
            <Link key={e.experimentId} to={`/experiments/${e.experimentId}`} className="compare-side">
              <span className="sub">{label(e)}</span>
              <span className={`compare-figure change-value ${e.overallPercentageChange === null ? "" : changeClassName(e.overallPercentageChange)}`}>
                {e.overallPercentageChange === null ? "–" : formatChangeText(e.overallPercentageChange)}
              </span>
              <span className="cell-sub">median across {e.queryResults.length} queries</span>
            </Link>
          ))}
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2 className="panel-title">Shared queries</h2>
          <span className="panel-meta">{rows.length} in common</span>
        </div>
        {rows.length === 0 ? (
          <div className="empty-state">
            <strong>These runs have no queries in common</strong>
            The built-in workload and each custom query use different query ids, so they can't be matched row by row.
            Compare two runs of the built-in workload instead.
          </div>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Query</th>
                  <th className="num">#{left.experimentId} before</th>
                  <th className="num">#{left.experimentId} after</th>
                  <th className="num">#{left.experimentId} change</th>
                  <th className="num">#{right.experimentId} before</th>
                  <th className="num">#{right.experimentId} after</th>
                  <th className="num">#{right.experimentId} change</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(({ left: l, right: r }) => (
                  <tr key={l.queryId}>
                    <td className="cell-main">{l.queryName}</td>
                    <td className="num tnum sub">{formatMs(l.baselineMedianMs)}</td>
                    <td className="num tnum">{formatMs(l.candidateMedianMs)}</td>
                    <td className="num">
                      <span className={`change-value ${changeClassName(l.percentageChange)}`}>{formatChangeText(l.percentageChange)}</span>
                    </td>
                    <td className="num tnum sub">{formatMs(r.baselineMedianMs)}</td>
                    <td className="num tnum">{formatMs(r.candidateMedianMs)}</td>
                    <td className="num">
                      <span className={`change-value ${changeClassName(r.percentageChange)}`}>{formatChangeText(r.percentageChange)}</span>
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
