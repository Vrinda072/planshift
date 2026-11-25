import { Link, useSearchParams } from "react-router-dom";
import type { Experiment } from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { changeClassName, formatBytes, formatChangeText, formatMs } from "../format";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { useExperimentsPolling } from "../hooks/useExperimentsPolling";
import { bestCandidate } from "../lib/candidates";
import { indexLabel } from "../lib/index";
import { strategyChange } from "../lib/plan";
import { STATUS_LABEL, isUnfinished } from "../lib/status";
import { evidenceNote } from "../lib/timing";

function finding(experiments: Experiment[]): string {
  const waiting = experiments.filter((e) => isUnfinished(e.status)).length;
  if (waiting > 0) {
    return `Measuring one at a time. ${experiments.length - waiting} of ${experiments.length} done.`;
  }
  const best = bestCandidate(experiments);
  if (!best) return "None of these indexes made a difference that counts, so none is worth its cost for this query.";
  const saved = Math.abs(best.queryResults[0].percentageChange).toFixed(1);
  const cost = best.indexSizeBytes != null ? `, for an index of ${formatBytes(best.indexSizeBytes)}` : "";
  return `${indexLabel(best)} saved the most: the median query was ${saved}% faster${cost}.`;
}

function Row({ experiment, isBest }: { experiment: Experiment; isBest: boolean }) {
  const result = experiment.queryResults[0];
  const change = result ? strategyChange(result.baselinePlanJson, result.candidatePlanJson) : null;
  return (
    <tr>
      <td>
        <Link to={`/experiments/${experiment.experimentId}`} className="row-link">
          <span className="cell-main mono">{experiment.targetColumn?.split(",").join(", ")}</span>
        </Link>
        {isBest && <span className="badge badge-improved" style={{ marginLeft: 8 }}>Most saved</span>}
        <div className="cell-sub">
          {result ? (change ? `${change.before} to ${change.after}` : "Same kind of scan as before") : " "}
        </div>
      </td>
      {result ? (
        <>
          <td className="num mono sub">{formatMs(result.baselineMedianMs)}</td>
          <td className="num mono">{formatMs(result.candidateMedianMs)}</td>
          <td className="num">
            <span className={`change-value ${changeClassName(result.percentageChange)}`}>
              {formatChangeText(result.percentageChange)}
            </span>
            <div className="cell-range">{evidenceNote(result, experiment.thresholdFraction)}</div>
          </td>
          <td className="num mono sub">
            {experiment.indexSizeBytes != null ? formatBytes(experiment.indexSizeBytes) : "–"}
          </td>
          <td className="num mono sub">
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
  const best = experiments && done ? bestCandidate(experiments) : null;

  return (
    <div>
      <PageHeader title="Candidate indexes" sub={experiments ? finding(experiments) : "Loading..."} />
      {error && <div className="error-banner">{error}</div>}
      {experiments && (
        <section className="panel">
          <div className="panel-head">
            <h2 className="panel-title">One query, {experiments.length} indexes on {experiments[0].targetTable}</h2>
            <span className="panel-meta">Each is created, measured and removed on its own</span>
          </div>
          <div className="table-scroll">
            <table>
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
