import { Link, useLocation } from "react-router-dom";
import { useHealthStatus } from "../hooks/useHealthStatus";

const LABEL = { checking: "Checking", online: "API connected", offline: "API unreachable" } as const;

const SECTIONS: Record<string, string> = {
  experiments: "Experiments",
  regressions: "Regressions",
  "query-builder": "Query builder",
  queries: "Workload",
};

function crumbsFor(pathname: string): { label: string; to?: string }[] {
  const [first, second] = pathname.split("/").filter(Boolean);
  if (!first) return [{ label: "Overview" }];
  const section = { label: SECTIONS[first] ?? first, to: `/${first}` };
  if (second) return [section, { label: first === "experiments" ? `#${second}` : second }];
  return [{ label: section.label }];
}

export function Topbar() {
  const { pathname } = useLocation();
  const health = useHealthStatus();
  const crumbs = crumbsFor(pathname);

  return (
    <header className="topbar">
      <div className="crumbs">
        {crumbs.map((c, i) => (
          <span key={c.label} style={{ display: "contents" }}>
            {i > 0 && <span className="crumb-sep">/</span>}
            {c.to ? (
              <Link to={c.to}>{c.label}</Link>
            ) : (
              <span className="crumb-current">{c.label}</span>
            )}
          </span>
        ))}
      </div>
      <div className="health" title="Live status of GET /api/health, checked every 15s">
        <span className={`health-dot ${health}`} />
        {LABEL[health]}
      </div>
    </header>
  );
}
