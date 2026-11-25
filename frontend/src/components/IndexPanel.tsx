import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { Experiment } from "../api/types";
import { createIndexSql } from "../lib/sql";
import { CopyButton } from "./CopyButton";

/** What to do with the tested index. The experiment has already removed its own copy. */
export function IndexPanel({ experiment }: { experiment: Experiment }) {
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
  const improved = experiment.queryResults.some((r) => r.status === "IMPROVED");

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
    <section className="panel">
      <div className="panel-head">
        <h2 className="panel-title">The index</h2>
        <span className="panel-meta">Removed again after the run</span>
      </div>
      <div className="panel-body">
        <p className="panel-note">
          {improved
            ? "To keep what this run measured, create the index yourself."
            : "This run didn't show an improvement, so there may be no reason to create it."}{" "}
          An index also takes space and slows writes, which isn't measured here.
        </p>
        <div className="sql-block">
          <pre className="code code-dark" style={{ paddingRight: 64 }}>{sql}</pre>
          <CopyButton text={sql} />
        </div>
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
        {error && <div className="error-banner" style={{ marginTop: 12, marginBottom: 0 }}>{error}</div>}
      </div>
    </section>
  );
}
