import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { ExperimentQueryResult } from "../api/types";
import { formatChangeText, formatMs } from "../format";

export function Regressions() {
  const [regressions, setRegressions] = useState<ExperimentQueryResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    api.listRegressions().then(setRegressions).catch((e) => setError(e.message));
  }, []);

  return (
    <div>
      <h1>Regressions</h1>
      <p className="subtitle">Every query result classified as a regression, across all experiments.</p>
      {error && <div className="error-banner">{error}</div>}
      {!error && regressions === null && <div className="loading-line">Loading...</div>}
      {regressions && regressions.length === 0 && (
        <div className="empty-state">No regressions detected in any experiment so far.</div>
      )}
      {regressions && regressions.length > 0 && (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Query</th>
                <th>Experiment</th>
                <th>Before</th>
                <th>After</th>
                <th>Change</th>
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
                  <td>{r.queryName}</td>
                  <td>#{r.experimentId}</td>
                  <td className="mono">{formatMs(r.baselineMedianMs)}</td>
                  <td className="mono">{formatMs(r.candidateMedianMs)}</td>
                  <td>
                    <span className="change-value change-positive">{formatChangeText(r.percentageChange)}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
