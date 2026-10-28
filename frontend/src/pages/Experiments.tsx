import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { Experiment } from "../api/types";
import { changeClassName, formatChangeText, formatDate } from "../format";
import { PageHeader } from "../components/PageHeader";
import { Skeleton } from "../components/Skeleton";

export function Experiments() {
  const [experiments, setExperiments] = useState<Experiment[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const [repetitions, setRepetitions] = useState(5);
  const [thresholdPercent, setThresholdPercent] = useState(20);
  const navigate = useNavigate();

  useEffect(() => {
    api.listExperiments().then(setExperiments).catch((e) => setError(e.message));
  }, []);

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
      <PageHeader
        title="Experiments"
        sub={
          <>
            Each run drops, then adds, an index on <code>orders.customer_id</code> and times the full 10-query
            workload both ways. For your own query and column, use the{" "}
            <Link to="/query-builder" style={{ color: "var(--accent)" }}>query builder</Link>.
          </>
        }
      />

      <section className="panel">
        <div className="panel-head">
          <h2 className="panel-title">Run the built-in experiment</h2>
        </div>
        <div className="panel-body">
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
                style={{ width: 150 }}
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
                style={{ width: 150 }}
              />
            </div>
            <button className="btn btn-primary" onClick={runExperiment} disabled={running}>
              {running ? "Starting..." : "Run experiment"}
            </button>
          </div>
          {error && <div className="error-banner" style={{ marginTop: 14, marginBottom: 0 }}>{error}</div>}
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2 className="panel-title">History</h2>
          {experiments && <span className="panel-meta">{experiments.length} total</span>}
        </div>
        {!error && experiments === null && <Skeleton rows={4} columns={7} />}
        {experiments && experiments.length === 0 && (
          <div className="empty-state">
            <strong>No experiments yet</strong>
            Run one above to see results here.
          </div>
        )}
        {experiments && experiments.length > 0 && (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Experiment</th>
                  <th>Target</th>
                  <th className="num">Reps</th>
                  <th className="num">Threshold</th>
                  <th>Ran</th>
                  <th>Status</th>
                  <th className="num">Result</th>
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
                    <td>
                      <span className="row-id">#{exp.experimentId}</span>{" "}
                      <span className="cell-main">{exp.experimentType === "CUSTOM_QUERY" ? "Custom query" : "Built-in workload"}</span>
                    </td>
                    <td className="mono sub">
                      {exp.targetTable ?? "orders"}.{exp.targetColumn ?? "customer_id"}
                    </td>
                    <td className="num mono">{exp.repetitions}</td>
                    <td className="num mono">{(exp.thresholdFraction * 100).toFixed(0)}%</td>
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
