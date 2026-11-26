import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { ExperimentQueryResult } from "../api/types";
import { formatChangeText, formatMs } from "../format";
import { PageHeader } from "../components/PageHeader";
import { Skeleton } from "../components/Skeleton";
import { outcome } from "../lib/outcome";

export function Regressions() {
  const [regressions, setRegressions] = useState<ExperimentQueryResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    api.listRegressions().then((all) => setRegressions(all.filter((r) => outcome(r) === "slower"))).catch((e) => setError(e.message));
  }, []);

  return (
    <div>
      <PageHeader
        title="Regressions"
        sub="Every query result that got slower past the threshold, across all experiments. Results with too few runs to check are left out."
      />
      {error && <div className="error-banner">{error}</div>}

      <section className="panel">
        <div className="panel-head">
          <h2 className="panel-title">Flagged results</h2>
          {regressions && <span className="panel-meta">{regressions.length} total</span>}
        </div>
        {!error && regressions === null && <Skeleton rows={4} columns={5} />}
        {regressions && regressions.length === 0 && (
          <div className="empty-state">
            <strong>Nothing regressed</strong>
            No query has gotten slower than its threshold in any experiment so far.
          </div>
        )}
        {regressions && regressions.length > 0 && (
          <div className="table-scroll">
            <table className="stack">
              <thead>
                <tr>
                  <th>Query</th>
                  <th>Experiment</th>
                  <th className="num">Before</th>
                  <th className="num">After</th>
                  <th className="num">Change</th>
                </tr>
              </thead>
              <tbody>
                {regressions.map((r) => (
                  <tr
                    key={r.id}
                    className="clickable"
                    onClick={() => navigate(`/experiments/${r.experimentId}`)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        navigate(`/experiments/${r.experimentId}`);
                      }
                    }}
                  >
                    <td className="cell-main" data-label="Query">{r.queryName}</td>
                    <td className="row-id" data-label="Experiment">#{r.experimentId}</td>
                    <td className="num sub" data-label="Before">{formatMs(r.baselineMedianMs)}</td>
                    <td className="num sub" data-label="After">{formatMs(r.candidateMedianMs)}</td>
                    <td className="num" data-label="Change">
                      <span className="change-value change-positive">{formatChangeText(r.percentageChange)}</span>
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
