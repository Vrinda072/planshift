import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { Experiment } from "../api/types";
import { changeClassName, formatChangeText, formatDate } from "../format";
import { Skeleton } from "../components/Skeleton";

export function Experiments() {
  const [experiments, setExperiments] = useState<Experiment[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const [repetitions, setRepetitions] = useState(5);
  const [thresholdPercent, setThresholdPercent] = useState(20);
  const navigate = useNavigate();

  const refresh = () => {
    api.listExperiments().then(setExperiments).catch((e) => setError(e.message));
  };

  useEffect(refresh, []);

  const runExperiment = async () => {
    setRunning(true);
    setError(null);
    try {
      // Returns almost immediately with the experiment in RUNNING state; the
      // result page itself polls for live progress from there.
      const result = await api.startExperiment(repetitions, thresholdPercent / 100);
      navigate(`/experiments/${result.experimentId}`);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setRunning(false);
    }
  };

  return (
    <div>
      <h1 className="accent-italic">Experiments</h1>
      <p className="subtitle">
        The built-in experiment: index added vs. index absent on <code>orders.customer_id</code>, run against
        the full 10-query workload. For a query and index target of your own choosing, use the{" "}
        <a href="/query-builder" style={{ color: "var(--accent)" }}>Query Builder</a> instead.
      </p>

      <div className="card">
        <h2>Run a new experiment</h2>
        <div className="form-row">
          <div className="form-field">
            <label htmlFor="repetitions">Repetitions per query</label>
            <input
              id="repetitions"
              type="number"
              min={1}
              max={20}
              value={repetitions}
              onChange={(e) => setRepetitions(Number(e.target.value))}
              disabled={running}
            />
          </div>
          <div className="form-field">
            <label htmlFor="threshold">Regression threshold (%)</label>
            <input
              id="threshold"
              type="number"
              min={1}
              max={100}
              value={thresholdPercent}
              onChange={(e) => setThresholdPercent(Number(e.target.value))}
              disabled={running}
            />
          </div>
          <button className="btn btn-primary" onClick={runExperiment} disabled={running}>
            {running ? "Starting..." : "Run Experiment"}
          </button>
        </div>
        {error && <div className="error-banner">{error}</div>}
      </div>

      <div className="section">
        <h2>All experiments</h2>
        {!error && experiments === null && <Skeleton rows={4} columns={7} />}
        {experiments && experiments.length === 0 && <div className="empty-state">No experiments yet.</div>}
        {experiments && experiments.length > 0 && (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Experiment</th>
                  <th>Target</th>
                  <th>Reps</th>
                  <th>Threshold</th>
                  <th>Ran</th>
                  <th>Status</th>
                  <th>Result</th>
                </tr>
              </thead>
              <tbody>
                {experiments.map((exp) => (
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
                    <td>#{exp.experimentId} &middot; {exp.experimentType === "CUSTOM_QUERY" ? "custom" : "built-in"}</td>
                    <td className="mono">
                      {exp.targetTable ?? "orders"}.{exp.targetColumn ?? "customer_id"}
                    </td>
                    <td className="mono">{exp.repetitions}</td>
                    <td className="mono">{(exp.thresholdFraction * 100).toFixed(0)}%</td>
                    <td>{formatDate(exp.createdAt)}</td>
                    <td>{exp.status}</td>
                    <td>
                      {exp.status === "COMPLETED" && exp.overallPercentageChange !== null ? (
                        <span className={`change-value ${changeClassName(exp.overallPercentageChange)}`}>
                          {formatChangeText(exp.overallPercentageChange)}
                        </span>
                      ) : (
                        "—"
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
