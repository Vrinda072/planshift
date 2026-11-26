import { Link, useSearchParams } from "react-router-dom";
import type { Experiment } from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { formatBytes, formatChangeText, formatMs } from "../format";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { useExperimentsPolling } from "../hooks/useExperimentsPolling";
import { candidateFinding, leadingCandidates } from "../lib/candidates";
import { OUTCOME_LABEL, changeClass, outcome } from "../lib/outcome";
import { planStorySummary } from "../lib/summary";
import { STATUS_LABEL, isUnfinished } from "../lib/status";
import { evidenceNote } from "../lib/timing";

function Row({ experiment, isBest }: { experiment: Experiment; isBest: boolean }) {
  const result = experiment.queryResults[0];
  return (
    <tr>
      <td data-label="Index">
        <Link to={`/experiments/${experiment.experimentId}`} className="row-link">
          <span className="cell-main">{experiment.targetColumn?.split(",").join(", ")}</span>
        </Link>
        {isBest && <span className="badge badge-faster" style={{ marginLeft: 8 }}>Most saved</span>}
        <div className="cell-sub">
          {result ? planStorySummary(result) : " "}
        </div>
      </td>
      {result ? (
        <>
          <td className="num sub" data-label="Before">{formatMs(result.baselineMedianMs)}</td>
          <td className="num" data-label="After">{formatMs(result.candidateMedianMs)}</td>
          <td className="num" data-label="Change">
            <span className={`change-value ${changeClass(result)}`}>
              {formatChangeText(result.percentageChange)}
            </span>
            <div className="cell-range">
              {outcome(result) === "unchecked"
                ? OUTCOME_LABEL.unchecked
                : outcome(result) === "unsure"
                  ? OUTCOME_LABEL.unsure
                  : evidenceNote(result, experiment.thresholdFraction)}
            </div>
          </td>
          <td className="num sub" data-label="Size">
            {experiment.indexSizeBytes != null ? formatBytes(experiment.indexSizeBytes) : "–"}
          </td>
          <td className="num sub" data-label="Build">
            {experiment.indexBuildMs != null ? formatMs(experiment.indexBuildMs) : "–"}
          </td>
        </>
      ) : (
        <td colSpan={5}>
          <span className={`status ${experiment.status.toLowerCase()}`}>{STATUS_LABEL[experiment.status]}</span>
        </td>
      )}
    </tr>
  );
}

export function Candidates() {
  const [params] = useSearchParams();
  const ids = params.get("ids") ?? "";
  const { experiments, error } = useExperimentsPolling(ids);
  useDocumentTitle("Candidate indexes");

  if (!ids) {
    return (
      <div>
        <PageHeader title="Candidate indexes" sub="Nothing to show yet. Start from the query builder." />
        <Link to="/query-builder" className="btn">Open the query builder</Link>
      </div>
    );
  }

  const done = experiments?.every((e) => !isUnfinished(e.status)) ?? false;
  const leaders = experiments && done ? leadingCandidates(experiments) : [];
  // A badge only when one candidate is ahead of the rest by more than this run's noise.
  const best = leaders.length === 1 ? leaders[0] : null;

  return (
    <div>
      <PageHeader title="Candidate indexes" sub={experiments ? candidateFinding(experiments) : "Loading..."} />
      {error && <div className="error-banner">{error}</div>}
      {experiments && (
        <section className="panel">
          <div className="panel-head">
            <h2 className="panel-title">One query, {experiments.length} indexes on {experiments[0].targetTable}</h2>
            <span className="panel-meta">Each is created, measured and removed on its own</span>
          </div>
          <div className="table-scroll">
            <table className="stack">
              <thead>
                <tr>
                  <th>Index columns, in order</th>
                  <th className="num">Before</th>
                  <th className="num">After</th>
                  <th className="num">Change</th>
                  <th className="num">Size</th>
                  <th className="num">Build</th>
                </tr>
              </thead>
              <tbody>
                {experiments.map((e) => (
                  <Row key={e.experimentId} experiment={e} isBest={best?.experimentId === e.experimentId} />
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
      <p className="panel-note">
        Each row has its own baseline, so compare the changes, not the raw times across rows. Gaps of a few tens of
        percent between rows can be timing noise. Size and build time are what the speedup costs; every extra index
        also slows writes, which this does not measure.
      </p>
    </div>
  );
}
