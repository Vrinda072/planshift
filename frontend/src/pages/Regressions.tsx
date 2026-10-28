import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { ExperimentQueryResult } from "../api/types";
import { formatChangeText, formatMs } from "../format";
import { PageHeader } from "../components/PageHeader";
import { Skeleton } from "../components/Skeleton";

export function Regressions() {
  const [regressions, setRegressions] = useState<ExperimentQueryResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    api.listRegressions().then(setRegressions).catch((e) => setError(e.message));
  }, []);

  return (
    <div>
      <PageHeader
        title="Regressions"
        sub="Every query result that got slower than the threshold, across all experiments."
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
            <table>
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
                    <td className="cell-main">{r.queryName}</td>
                    <td className="row-id">#{r.experimentId}</td>
                    <td className="num mono sub">{formatMs(r.baselineMedianMs)}</td>
                    <td className="num mono sub">{formatMs(r.candidateMedianMs)}</td>
                    <td className="num">
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
