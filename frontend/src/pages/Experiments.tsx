import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { Experiment } from "../api/types";
import { changeClassName, formatChangeText, formatDate } from "../format";
import { PageHeader } from "../components/PageHeader";
import { Skeleton } from "../components/Skeleton";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { indexLabel } from "../lib/index";

type SortKey = "id" | "ran" | "result";
type StatusFilter = "ALL" | "COMPLETED" | "RUNNING" | "FAILED";

const FILTERS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "COMPLETED", label: "Completed" },
  { value: "RUNNING", label: "Running" },
  { value: "FAILED", label: "Failed" },
];

export function Experiments() {
  const [experiments, setExperiments] = useState<Experiment[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const [repetitions, setRepetitions] = useState(5);
  const [thresholdPercent, setThresholdPercent] = useState(20);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [sort, setSort] = useState<{ key: SortKey; dir: 1 | -1 }>({ key: "id", dir: -1 });
  const [selected, setSelected] = useState<number[]>([]);
  const navigate = useNavigate();
  useDocumentTitle("Experiments");

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

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    const filtered = (experiments ?? []).filter((e) => {
      if (status !== "ALL" && e.status !== status) return false;
      if (!q) return true;
      const hay = `#${e.experimentId} ${indexLabel(e)} ${e.experimentType}`.toLowerCase();
      return hay.includes(q);
    });
    const value = (e: Experiment) =>
      sort.key === "id" ? e.experimentId : sort.key === "ran" ? Date.parse(e.createdAt) : e.overallPercentageChange ?? Infinity;
    return [...filtered].sort((a, b) => (value(a) - value(b)) * sort.dir);
  }, [experiments, search, status, sort]);

  const toggleSort = (key: SortKey) =>
    setSort((s) => (s.key === key ? { key, dir: (s.dir * -1) as 1 | -1 } : { key, dir: -1 }));

  const toggleSelected = (id: number) =>
    setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : s.length >= 2 ? [s[1], id] : [...s, id]));

  const sortMark = (key: SortKey) => (sort.key === key ? (sort.dir === 1 ? " ↑" : " ↓") : "");

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
        actions={
          <div className="run-inline">
            <label className="run-inline-field">
              <span>Runs</span>
              <input className="input input-num" type="number" min={1} max={20} value={repetitions}
                onChange={(e) => setRepetitions(Number(e.target.value))} disabled={running} aria-label="Repetitions per query" />
            </label>
            <label className="run-inline-field">
              <span>Threshold %</span>
              <input className="input input-num" type="number" min={1} max={100} value={thresholdPercent}
                onChange={(e) => setThresholdPercent(Number(e.target.value))} disabled={running} aria-label="Regression threshold percent" />
            </label>
            <button className="btn btn-primary" onClick={runExperiment} disabled={running}>
              {running ? "Starting..." : "Run experiment"}
            </button>
          </div>
        }
      />

      {error && <div className="error-banner">{error}</div>}

      <section className="panel">
        <div className="toolbar">
          <div className="segmented" role="tablist" aria-label="Filter by status">
            {FILTERS.map((f) => (
              <button key={f.value} role="tab" aria-selected={status === f.value}
                className={status === f.value ? "active" : ""} onClick={() => setStatus(f.value)}>
                {f.label}
              </button>
            ))}
          </div>
          <input className="input" style={{ width: 220 }} placeholder="Filter by id or column" value={search}
            onChange={(e) => setSearch(e.target.value)} aria-label="Filter experiments" />
          <div className="toolbar-spacer" />
          {selected.length > 0 && (
            <span className="panel-meta">{selected.length} selected</span>
          )}
          <button className="btn btn-sm" disabled={selected.length !== 2}
            onClick={() => navigate(`/compare?a=${Math.min(...selected)}&b=${Math.max(...selected)}`)}
            title={selected.length === 2 ? "Compare the two selected runs" : "Select two runs to compare"}>
            Compare
          </button>
        </div>

        {!error && experiments === null && <Skeleton rows={4} columns={7} />}
        {experiments && experiments.length === 0 && (
          <div className="empty-state">
            <strong>No experiments yet</strong>
            Run the built-in one above, or build your own query.
          </div>
        )}
        {experiments && experiments.length > 0 && rows.length === 0 && (
          <div className="empty-state">
            <strong>No matches</strong>
            Nothing fits this filter.
          </div>
        )}
        {rows.length > 0 && (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th style={{ width: 36 }} />
                  <th><button className="th-sort" onClick={() => toggleSort("id")}>Experiment{sortMark("id")}</button></th>
                  <th>Index on</th>
                  <th className="num">Runs</th>
                  <th className="num">Threshold</th>
                  <th><button className="th-sort" onClick={() => toggleSort("ran")}>Ran{sortMark("ran")}</button></th>
                  <th>Status</th>
                  <th className="num"><button className="th-sort" onClick={() => toggleSort("result")}>Change{sortMark("result")}</button></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((exp) => (
                  <tr key={exp.experimentId} className="clickable" onClick={() => navigate(`/experiments/${exp.experimentId}`)}>
                    <td onClick={(e) => e.stopPropagation()}>
                      <input type="checkbox" checked={selected.includes(exp.experimentId)}
                        onChange={() => toggleSelected(exp.experimentId)} aria-label={`Select experiment ${exp.experimentId}`} />
                    </td>
                    <td>
                      <Link to={`/experiments/${exp.experimentId}`} className="row-link" onClick={(e) => e.stopPropagation()}>
                        <span className="row-id">#{exp.experimentId}</span>{" "}
                        <span className="cell-main">{exp.experimentType === "CUSTOM_QUERY" ? "Custom query" : "Built-in workload"}</span>
                      </Link>
                    </td>
                    <td className="mono sub">{indexLabel(exp)}</td>
                    <td className="num tnum">{exp.repetitions}</td>
                    <td className="num tnum">{(exp.thresholdFraction * 100).toFixed(0)}%</td>
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
