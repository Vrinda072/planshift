import type { Experiment } from "../api/types";

/** What was measured: the SQL of a one-query run. Results recorded before it was kept have nothing to show. */
export function QueryLine({ experiment }: { experiment: Experiment }) {
  const results = experiment.queryResults;
  const sql = results.length === 1 ? results[0].querySql : null;
  if (!sql) return null;
  return (
    <pre className="code query-line" aria-label="The query that was measured">
      {sql}
    </pre>
  );
}
