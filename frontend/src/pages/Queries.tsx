import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import type { WorkloadQuery } from "../api/types";
import { CopyButton } from "../components/CopyButton";
import { PageHeader } from "../components/PageHeader";

export function Queries() {
  const [queries, setQueries] = useState<WorkloadQuery[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.listQueries().then(setQueries).catch((e) => setError(e.message));
  }, []);

  return (
    <div>
      <PageHeader
        title="Workload"
        sub={
          <>
            The fixed, read-only queries the built-in experiment runs. No arbitrary SQL is accepted here; to try
            your own, use the{" "}
            <Link to="/query-builder" style={{ color: "var(--accent)" }}>query builder</Link>.
          </>
        }
      />
      {error && <div className="error-banner">{error}</div>}
      {!error && queries === null && <div className="loading-line">Loading...</div>}

      {queries && (
        <section className="panel">
          <div className="panel-head">
            <h2 className="panel-title">Benchmark queries</h2>
            <span className="panel-meta">{queries.length} queries</span>
          </div>
          {queries.map((q, i) => (
            <div className="step" key={q.id} style={{ animation: `fadeInUp 240ms var(--ease-out) ${i * 30}ms both` }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, marginBottom: 4 }}>
                <span className="cell-main" style={{ fontSize: 14 }}>{q.name}</span>
                <span className="tag">{q.category.replace(/_/g, " ")}</span>
              </div>
              <p style={{ margin: "0 0 12px", color: "var(--ink-2)", fontSize: 13 }}>{q.description}</p>
              <div className="sql-block">
                <pre className="code" style={{ paddingRight: 64 }}>{q.sql}</pre>
                <CopyButton text={q.sql} />
              </div>
            </div>
          ))}
        </section>
      )}
    </div>
  );
}
