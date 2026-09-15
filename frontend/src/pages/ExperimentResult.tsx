import { Fragment, useState } from "react";
import { useParams } from "react-router-dom";
import { changeClassName, formatChangeText, formatMs } from "../format";
import { PlanTreeCompare } from "../components/PlanTreeCompare";
import { ExperimentProgress } from "../components/ExperimentProgress";
import { InfoTooltip } from "../components/InfoTooltip";
import { QueryComparisonChart } from "../components/QueryComparisonChart";
import { AnimatedNumber } from "../components/AnimatedNumber";
import { useExperimentPolling } from "../hooks/useExperimentPolling";

export function ExperimentResult() {
  const { id } = useParams<{ id: string }>();
  const { experiment, error } = useExperimentPolling(id ? Number(id) : null);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

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
        <div className="category-tag">Experiment #{experiment.experimentId}</div>
        <h1 className="accent-italic">Running experiment</h1>
        <ExperimentProgress currentPhase={experiment.currentPhase} />
      </div>
    );
  }

  if (experiment.status === "FAILED") {
    return (
      <div>
        <div className="category-tag">Experiment #{experiment.experimentId}</div>
        <h1 className="accent-italic">Experiment failed</h1>
        <div className="error-banner">
          Something went wrong while running this experiment. Check the backend logs for details.
        </div>
      </div>
    );
  }

  const regressions = experiment.queryResults.filter((r) => r.status === "REGRESSED");

  return (
    <div>
      <div className="category-tag">Experiment #{experiment.experimentId}</div>
      <h1 className="accent-italic">Experiment complete</h1>

      {experiment.overallPercentageChange !== null && (
        <div className="card" style={{ marginBottom: 24, marginTop: 16 }}>
          <div className="category-tag">
            Overall performance
            <InfoTooltip text="The median of each query's percentage change. Median (not average) so one especially large swing in a single query doesn't dominate the headline number." />
          </div>
          <div className={`change-value ${changeClassName(experiment.overallPercentageChange)}`} style={{ fontSize: 32 }}>
            <AnimatedNumber value={experiment.overallPercentageChange} format={formatChangeText} />
          </div>
        </div>
      )}

      {regressions.length > 0 && (
        <div className="error-banner">
          ⚠ {regressions.length} regression{regressions.length > 1 ? "s" : ""} detected:{" "}
          {regressions.map((r) => r.queryName).join(", ")}
        </div>
      )}

      <div className="section">
        <h2>Per-query results</h2>
        <p style={{ color: "var(--text-tertiary)", fontSize: 12, marginTop: -8 }}>
          Median of {experiment.repetitions} runs per configuration &middot; regression threshold{" "}
          {(experiment.thresholdFraction * 100).toFixed(0)}%
        </p>
        <div className="card" style={{ marginBottom: 20 }}>
          <QueryComparisonChart results={experiment.queryResults} />
        </div>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Query</th>
                <th>Before</th>
                <th>After</th>
                <th>Change</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {experiment.queryResults.map((r) => {
                const isOpen = expanded.has(r.queryId);
                return (
                  <Fragment key={r.queryId}>
                    <tr>
                      <td>
                        {r.queryName}
                        <div>
                          <button
                            className="see-why-toggle"
                            onClick={() => toggle(r.queryId)}
                            aria-expanded={isOpen}
                          >
                            {isOpen ? "Hide plan" : "See why"}
                          </button>
                        </div>
                      </td>
                      <td className="mono"><AnimatedNumber value={r.baselineMedianMs} format={formatMs} /></td>
                      <td className="mono"><AnimatedNumber value={r.candidateMedianMs} format={formatMs} /></td>
                      <td>
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
                          {r.status}
                        </span>
                      </td>
                    </tr>
                    {/* Always mounted (rather than only rendered when open) so the
                        expand/collapse can animate its height smoothly via the
                        accordion grid-rows trick -- a table row can't otherwise
                        transition from height 0, since row height isn't a real
                        animatable CSS property. */}
                    <tr className="no-entrance">
                      <td colSpan={5} style={{ padding: 0, border: "none" }}>
                        <div className={`accordion${isOpen ? " open" : ""}`}>
                          <div className="accordion-inner">
                            <div style={{ background: "var(--bg)", padding: "12px" }}>
                              <div className="query-sql" style={{ marginBottom: 4 }}>{r.planDiffSummary}</div>
                              <div style={{ color: "var(--text-tertiary)", fontSize: 11.5, marginBottom: 12 }}>
                                Each box below is one step ("node") in Postgres's real execution plan for this query.
                                Boxes outlined in blue changed between before and after.
                              </div>
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
      </div>
    </div>
  );
}
