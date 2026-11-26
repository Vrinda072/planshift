import { Fragment, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { Experiment, ExperimentQueryResult } from "../api/types";
import { formatBytes, formatChangeText, formatDate, formatMs } from "../format";
import { ResultBlocks } from "../components/ResultBlocks";
import { ResultCards } from "../components/ResultCards";
import { headline } from "../lib/summary";
import { ExperimentProgress } from "../components/ExperimentProgress";
import { PageHeader } from "../components/PageHeader";
import { PlanTreeCompare } from "../components/PlanTreeCompare";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { useExperimentPolling } from "../hooks/useExperimentPolling";
import { exportCsv, exportJson } from "../lib/export";
import { strategyChange } from "../lib/plan";
import { indexLabel, isControl, typeLabel } from "../lib/index";
import { clockName, evidenceNote, range } from "../lib/timing";
import { TimingDetail } from "../components/TimingDetail";
import { IndexPanel } from "../components/IndexPanel";
import { QueryLine } from "../components/QueryLine";
import { OUTCOME_LABEL, changeClass, outcome } from "../lib/outcome";
import { RUN_ORDER_LABEL } from "../lib/runOrder";
import { belowNoiseFloor } from "../lib/noise";
import { IconDownload } from "../components/Icons";

/** {@code compact} leaves out what a finished one-query result already shows above. */
function MetaRow({ experiment, compact = false }: { experiment: Experiment; compact?: boolean }) {
  const target = indexLabel(experiment);
  return (
    <div className="meta-row">
      <span className="meta-item">
        <span className="meta-k">Type</span>
        {typeLabel(experiment)}
      </span>
      {!compact && (
        <span className="meta-item">
          <span className="meta-k">{isControl(experiment) ? "Table" : "Index on"}</span>
          <span className="mono">{target}</span>
        </span>
      )}
      {!compact && experiment.indexSizeBytes != null && experiment.indexBuildMs != null && (
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
      {experiment.measurement && (
        <span className="meta-item">
          <span className="meta-k">Run order</span>
          {RUN_ORDER_LABEL[experiment.measurement]}
        </span>
      )}
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

function Details({ result }: { result: ExperimentQueryResult }) {
  return (
    <>
      {result.timing && <TimingDetail timing={result.timing} />}
      <h3>Full query plan</h3>
      <p className="accordion-summary">
        {result.planDiffSummary.replace(/Scan\/execution strategy changed: /g, "")}
      </p>
      <p className="accordion-hint">
        Each box is one step in Postgres's real execution plan. Outlined boxes changed between before and after.
      </p>
      <PlanTreeCompare baselinePlanJson={result.baselinePlanJson} candidatePlanJson={result.candidatePlanJson} />
    </>
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
      const started = await api.startExperiment(
        experiment.repetitions, experiment.thresholdFraction, false, experiment.measurement);
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
        <ExperimentProgress currentPhase={experiment.currentPhase} measurement={experiment.measurement} />
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
  const single = results.length === 1 ? results[0] : null;
  const target = indexLabel(experiment);
  return (
    <div>
      <PageHeader
        title={headline(experiment)}
        sub={
          <>
            Experiment #{experiment.experimentId} · {isControl(experiment) ? "table" : "index on"} {target}{" "}
            <span className="status completed">Completed</span>
          </>
        }
        actions={
          <>
            <button className="btn" onClick={copyLink}>{copied ? "Copied" : "Copy link"}</button>
            <button className="btn" onClick={() => exportCsv(experiment)}><IconDownload />CSV</button>
            <button className="btn" onClick={() => exportJson(experiment)}><IconDownload />JSON</button>
            {experiment.experimentType === "INDEX_ADD" && (
              <button className="btn btn-primary" onClick={rerun} disabled={rerunning}>
                {rerunning ? "Starting..." : "Run again"}
              </button>
            )}
          </>
        }
      />

      {belowNoiseFloor(results) && (
        <div className="inline-warning">
          Every query here ran in under 1 ms, which is mostly timer jitter. Try a larger table before reading much into
          the percentages.
        </div>
      )}

      <QueryLine experiment={experiment} />
      <ResultBlocks experiment={experiment} />
      {!isControl(experiment) && results.some((r) => r.timing) && (
        <p className="trust-line">
          Even a passed timing check can come from the machine speeding up or slowing down. An A/A check shows how often
          that happens here.
        </p>
      )}

      {single ? (
        <>
          <ResultCards experiment={experiment} planOpen={expanded.has(single.queryId)}
            onShowPlan={() => toggle(single.queryId)} />
          <div className={`accordion${expanded.has(single.queryId) ? " open" : ""}`}>
            <div className="accordion-inner">
              <div className="accordion-body">
                <Details result={single} />
              </div>
            </div>
          </div>
        </>
      ) : (
        <>
          <section className="panel">
            <div className="panel-head">
              <h2 className="panel-title">Each query</h2>
              <span className="panel-meta">
                Median of all runs{results[0].timing && `, ${clockName(results[0].timing)}`}, with the fastest and
                slowest below
              </span>
            </div>
            <div className="table-scroll">
              <table className="stack">
                <thead>
                  <tr>
                    <th>Query</th>
                    <th className="num">Before</th>
                    <th className="num">After</th>
                    <th className="num">Change</th>
                    <th>Verdict</th>
                  </tr>
                </thead>
                <tbody>
                  {results.map((r) => {
                    const isOpen = expanded.has(r.queryId);
                    const change = strategyChange(r.baselinePlanJson, r.candidatePlanJson);
                    return (
                      <Fragment key={r.queryId}>
                        <tr>
                          <td data-label="Query">
                            <div className="cell-main">{r.queryName}</div>
                            {change && <div className="cell-sub">{change.before} to {change.after}</div>}
                            <button className="btn-link see-why-toggle" onClick={() => toggle(r.queryId)}
                              aria-expanded={isOpen}>
                              {isOpen ? "Hide details" : "See runs and plan"}
                            </button>
                          </td>
                          <td className="num sub" data-label="Before">
                            <span className="cell-time">{formatMs(r.baselineMedianMs)}</span>
                            {r.timing && <div className="cell-range">{range(r.timing.baselineSamplesMs)}</div>}
                          </td>
                          <td className="num" data-label="After">
                            <span className="cell-time">{formatMs(r.candidateMedianMs)}</span>
                            {r.timing && <div className="cell-range">{range(r.timing.candidateSamplesMs)}</div>}
                          </td>
                          <td className="num" data-label="Change">
                            <span className={`change-value ${changeClass(r)}`}>
                              {formatChangeText(r.percentageChange)}
                            </span>
                          </td>
                          <td data-label="Verdict">
                            <span className={`badge badge-${outcome(r)}`}>{OUTCOME_LABEL[outcome(r)]}</span>
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
                                  <Details result={r} />
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
          <div className="cards cards-single"><IndexPanel experiment={experiment} showCost /></div>
        </>
      )}

      <MetaRow experiment={experiment} compact={single !== null} />
    </div>
  );
}
