import { Fragment, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { Experiment } from "../api/types";
import { changeClassName, formatBytes, formatChangeText, formatDate, formatMs } from "../format";
import { AnimatedNumber } from "../components/AnimatedNumber";
import { Hero } from "../components/Hero";
import { ExperimentProgress } from "../components/ExperimentProgress";
import { PageHeader } from "../components/PageHeader";
import { PlanTreeCompare } from "../components/PlanTreeCompare";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { useExperimentPolling } from "../hooks/useExperimentPolling";
import { exportCsv, exportJson } from "../lib/export";
import { strategyChange } from "../lib/plan";
import { indexLabel, isControl, typeLabel } from "../lib/index";
import { evidenceNote, range } from "../lib/timing";
import { TimingDetail } from "../components/TimingDetail";
import { belowNoiseFloor } from "../lib/noise";
import { IconDownload } from "../components/Icons";

function MetaRow({ experiment }: { experiment: Experiment }) {
  const target = indexLabel(experiment);
  return (
    <div className="meta-row">
      <span className="meta-item">
        <span className="meta-k">Type</span>
        {typeLabel(experiment)}
      </span>
      <span className="meta-item">
        <span className="meta-k">{isControl(experiment) ? "Table" : "Index on"}</span>
        <span className="mono">{target}</span>
      </span>
      {experiment.indexSizeBytes != null && experiment.indexBuildMs != null && (
        <span className="meta-item">
          <span className="meta-k">Index cost</span>
          <span className="mono">
            {formatBytes(experiment.indexSizeBytes)}, built in {formatMs(experiment.indexBuildMs)}
          </span>
        </span>
      )}
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

  if (experiment.status === "QUEUED") {
    return (
      <div>
        <PageHeader title={`Experiment #${experiment.experimentId}`}>
          <MetaRow experiment={experiment} />
        </PageHeader>
        <section className="panel">
          <div className="panel-body">
            <span className="status queued">Queued</span>
            <p className="panel-note" style={{ margin: "8px 0 0" }}>
              Experiments run one at a time, so that none of them is measured while another is changing indexes. This
              one starts when those ahead of it finish, and this page updates by itself.
            </p>
          </div>
        </section>
      </div>
    );
  }

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
            {experiment.experimentType === "INDEX_ADD" && (
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
          <span className="panel-meta">Median of all runs, with the fastest and slowest below</span>
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
                          {isOpen ? "Hide details" : "See runs and plan"}
                        </button>
                      </td>
                      <td className="num mono sub">
                        <AnimatedNumber value={r.baselineMedianMs} format={formatMs} />
                        {r.timing && <div className="cell-range">{range(r.timing.baselineSamplesMs)}</div>}
                      </td>
                      <td className="num mono">
                        <AnimatedNumber value={r.candidateMedianMs} format={formatMs} />
                        {r.timing && <div className="cell-range">{range(r.timing.candidateSamplesMs)}</div>}
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
                        <div className="cell-range">{evidenceNote(r, experiment.thresholdFraction)}</div>
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
                              {r.timing && <TimingDetail timing={r.timing} />}
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
