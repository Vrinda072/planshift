import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { Experiment } from "../api/types";
import { changeClassName, formatChangeText, formatDate } from "../format";
import { ExplainerPanel } from "../components/ExplainerPanel";
import { Skeleton } from "../components/Skeleton";

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
