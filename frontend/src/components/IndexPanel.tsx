import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { Experiment } from "../api/types";
import { formatBytes, formatMs } from "../format";
import { outcome } from "../lib/outcome";
import { createIndexSql } from "../lib/sql";
import { CopyButton } from "./CopyButton";

/** What to do with the tested index. The experiment has already removed its own copy. */
export function IndexPanel({ experiment, showCost = false }: { experiment: Experiment; showCost?: boolean }) {
  const sql = createIndexSql(experiment);
  const [canKeep, setCanKeep] = useState(false);
  // The last answer from keeping or removing, which is newer than what the page was loaded with.
  const [changed, setChanged] = useState<Experiment | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Without an answer the button stays hidden; the SQL is still there to copy.
    api.getConfig().then((c) => setCanKeep(c.keepIndexEnabled)).catch(() => {});
  }, []);

  if (!sql) return null;
  const current = changed?.experimentId === experiment.experimentId ? changed : experiment;
  const kept = current.keptIndexName;
  const improved = experiment.queryResults.some((r) => outcome(r) === "faster");
  const unsure = !improved && experiment.queryResults.some((r) => outcome(r) === "unsure");
  const unchecked = !improved && experiment.queryResults.some((r) => outcome(r) === "unchecked");

  const change = async (action: (id: number) => Promise<Experiment>) => {
    setBusy(true);
    setError(null);
    try {
      setChanged(await action(experiment.experimentId));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card">
      <h2>{improved ? "Keep this index" : "The tested index"}</h2>
      {!improved && (
        <p>
          {unchecked
            ? "This older result wasn't timing-checked, so it can't say whether the index helps."
            : unsure
            ? "This run had too few runs to tell whether the index helps."
            : "This run didn't show an improvement, so there may be no reason to create it."}
        </p>
      )}
      {showCost && experiment.indexSizeBytes != null && experiment.indexBuildMs != null && (
        <p>
          It took {formatBytes(experiment.indexSizeBytes)} of disk space and {formatMs(experiment.indexBuildMs)} to
          build. Its effect on writes isn't measured here.
        </p>
      )}
      <div className="sql-block">
        <pre className="code code-dark">{sql}</pre>
        <CopyButton text={sql} />
      </div>
      <p className="card-note">The experiment removed its own copy.</p>
      {canKeep && (
        <div className="keep-row">
          {kept ? (
            <>
              <span>
                Created here as <span className="mono">{kept}</span>. Later experiments on this table will have it on
                both sides.
              </span>
              <button className="btn btn-sm" onClick={() => change(api.removeKeptIndex)} disabled={busy}>
                {busy ? "Removing..." : "Remove it"}
              </button>
            </>
          ) : (
            <button className="btn btn-sm" onClick={() => change(api.keepIndex)} disabled={busy}>
              {busy ? "Creating..." : "Create it on this database"}
            </button>
          )}
        </div>
      )}
      {error && <div className="error-banner">{error}</div>}
    </section>
  );
}
