import { Fragment, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { Experiment } from "../api/types";
import { changeClassName, formatChangeText, formatDate, formatMs } from "../format";
import { AnimatedNumber } from "../components/AnimatedNumber";
import { Hero } from "../components/Hero";
import { ExperimentProgress } from "../components/ExperimentProgress";
import { PageHeader } from "../components/PageHeader";
import { PlanTreeCompare } from "../components/PlanTreeCompare";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { useExperimentPolling } from "../hooks/useExperimentPolling";
import { exportCsv, exportJson } from "../lib/export";
import { strategyChange } from "../lib/plan";
import { indexLabel } from "../lib/index";
import { belowNoiseFloor } from "../lib/noise";
import { IconDownload } from "../components/Icons";

function MetaRow({ experiment }: { experiment: Experiment }) {
  const target = indexLabel(experiment);
  return (
    <div className="meta-row">
      <span className="meta-item">
        <span className="meta-k">Type</span>
        {experiment.experimentType === "CUSTOM_QUERY" ? "Custom query" : "Built-in workload"}
      </span>
      <span className="meta-item">
        <span className="meta-k">Index on</span>
        <span className="mono">{target}</span>
      </span>
      <span className="meta-item">
        <span className="meta-k">Runs</span>
        <span className="mono">{experiment.repetitions} per query</span>
      </span>
      <span className="meta-item">
        <span className="meta-k">Threshold</span>
        <span className="mono">{(experiment.thresholdFraction * 100).toFixed(0)}%</span>
      </span>
      <span className="meta-item">
        <span className="meta-k">Ran</span>
        {formatDate(experiment.createdAt)}
      </span>
    </div>
  );
}

export function ExperimentResult() {
  const { id } = useParams<{ id: string }>();
  const { experiment, error } = useExperimentPolling(id ? Number(id) : null);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [copied, setCopied] = useState(false);
  const [rerunning, setRerunning] = useState(false);
  const navigate = useNavigate();
  useDocumentTitle(`Experiment #${id ?? ""}`);

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // clipboard unavailable (insecure context); nothing useful to do
    }
  };

  const rerun = async () => {
    if (!experiment) return;
    setRerunning(true);
    try {
      const started = await api.startExperiment(experiment.repetitions, experiment.thresholdFraction);
      navigate(`/experiments/${started.experimentId}`);
    } finally {
      setRerunning(false);
    }
  };

  const toggle = (queryId: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(queryId)) next.delete(queryId);
      else next.add(queryId);
      return next;
    });
  };

  if (error) return <div className="error-banner">{error}</div>;
  if (!experiment) return <div className="loading-line">Loading experiment...</div>;

  if (experiment.status === "RUNNING") {
    return (
      <div>
        <PageHeader title={`Experiment #${experiment.experimentId}`}>
          <MetaRow experiment={experiment} />
        </PageHeader>
        <ExperimentProgress currentPhase={experiment.currentPhase} />
      </div>
    );
  }

  if (experiment.status === "FAILED") {
    return (
      <div>
        <PageHeader title={`Experiment #${experiment.experimentId}`}>
          <MetaRow experiment={experiment} />
        </PageHeader>
        <div className="error-banner">
          Something went wrong while running this experiment. Check the backend logs for details.
        </div>
      </div>
    );
  }

  const results = experiment.queryResults;
  return (
    <div>
      <PageHeader
        title={
          <>
            Experiment #{experiment.experimentId}{" "}
            <span className="status completed" style={{ verticalAlign: "middle", marginLeft: 6, fontSize: 13, fontWeight: 400, letterSpacing: 0 }}>
              Completed
            </span>
          </>
        }
        actions={
          <>
            <button className="btn btn-sm" onClick={copyLink}>{copied ? "Copied" : "Copy link"}</button>
            <button className="btn btn-sm" onClick={() => exportCsv(experiment)}><IconDownload />CSV</button>
            <button className="btn btn-sm" onClick={() => exportJson(experiment)}><IconDownload />JSON</button>
            {experiment.experimentType !== "CUSTOM_QUERY" && (
              <button className="btn btn-primary btn-sm" onClick={rerun} disabled={rerunning}>
                {rerunning ? "Starting..." : "Run again"}
              </button>
            )}
          </>
        }
      >
        <MetaRow experiment={experiment} />
      </PageHeader>

      {belowNoiseFloor(results) && (
        <div className="inline-warning" style={{ marginBottom: 16 }}>
          Every query here ran in under 1 ms, which is mostly timer jitter. Try a larger table before reading much into
          the percentages.
        </div>
      )}

      <Hero experiment={experiment} eyebrow={<>Result · threshold {(experiment.thresholdFraction * 100).toFixed(0)}%</>} />

      <section className="panel">
        <div className="panel-head">
          <h2 className="panel-title">Results</h2>
          <span className="panel-meta">Expand a row to see what the planner changed</span>
        </div>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Query</th>
                <th className="num">Before</th>
                <th className="num">After</th>
                <th className="num">Change</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {results.map((r) => {
                const isOpen = expanded.has(r.queryId);
                return (
                  <Fragment key={r.queryId}>
                    <tr>
                      <td>
                        <div className="cell-main">{r.queryName}</div>
                        {(() => {
                          const change = strategyChange(r.baselinePlanJson, r.candidatePlanJson);
                          return change ? <div className="cell-sub">{change.before} to {change.after}</div> : null;
                        })()}
                        <button className="see-why-toggle" onClick={() => toggle(r.queryId)} aria-expanded={isOpen}>
                          {isOpen ? "Hide plan" : "See plan"}
                        </button>
                      </td>
                      <td className="num mono sub">
                        <AnimatedNumber value={r.baselineMedianMs} format={formatMs} />
                      </td>
                      <td className="num mono">
                        <AnimatedNumber value={r.candidateMedianMs} format={formatMs} />
                      </td>
                      <td className="num">
                        <span className={`change-value ${changeClassName(r.percentageChange)}`}>
                          {formatChangeText(r.percentageChange)}
                        </span>
                      </td>
                      <td>
                        <span
                          className={`badge ${
                            r.status === "IMPROVED" ? "badge-improved" : r.status === "REGRESSED" ? "badge-regressed" : "badge-unchanged"
                          }`}
                        >
                          {r.status === "IMPROVED" ? "Improved" : r.status === "REGRESSED" ? "Regressed" : "Unchanged"}
                        </span>
                      </td>
                    </tr>
                    {/* Always mounted so the expand/collapse can animate its height
                        via the grid-rows trick; a table row can't transition from
                        height 0 on its own. */}
                    <tr className="no-entrance accordion-row">
                      <td colSpan={5}>
                        <div className={`accordion${isOpen ? " open" : ""}`}>
                          <div className="accordion-inner">
                            <div className="accordion-body">
                              <p className="accordion-summary">{r.planDiffSummary}</p>
                              <p className="accordion-hint">
                                Each box is one step in Postgres's real execution plan for this query. Highlighted
                                boxes changed between before and after.
                              </p>
                              <PlanTreeCompare baselinePlanJson={r.baselinePlanJson} candidatePlanJson={r.candidatePlanJson} />
                            </div>
                          </div>
                        </div>
                      </td>
                    </tr>
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
