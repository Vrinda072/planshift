import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { WorkloadQuery } from "../api/types";
import { CopyButton } from "../components/CopyButton";

export function Queries() {
  const [queries, setQueries] = useState<WorkloadQuery[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.listQueries().then(setQueries).catch((e) => setError(e.message));
  }, []);

  return (
    <div>
      <h1 className="accent-italic">Queries</h1>
      <p className="subtitle">
        The fixed, read-only workload PLANSHIFT benchmarks against the synthetic dataset. No arbitrary SQL is
        accepted here -- but you can build your own query against your own data on the{" "}
        <a href="/query-builder" style={{ color: "var(--accent)" }}>Query Builder</a> page.
      </p>
      {error && <div className="error-banner">{error}</div>}
      {!error && queries === null && <div className="loading-line">Loading...</div>}
      {queries &&
        queries.map((q) => (
          <div className="card card-hoverable" key={q.id} style={{ marginBottom: 16 }}>
            <div className="category-tag">{q.category.replace(/_/g, " ")}</div>
            <h2 style={{ marginTop: 4 }}>{q.name}</h2>
            <p style={{ color: "var(--text-secondary)", marginTop: 0 }}>{q.description}</p>
            <div className="sql-block">
              <div className="query-sql" style={{ background: "var(--bg)", padding: 12, borderRadius: 6, border: "1px solid var(--border-strong)" }}>
                {q.sql}
              </div>
              <CopyButton text={q.sql} />
            </div>
          </div>
        ))}
    </div>
  );
}
