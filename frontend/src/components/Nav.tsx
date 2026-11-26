import { useEffect, useState } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import { api } from "../api/client";
import { outcome } from "../lib/outcome";
import { useHealthStatus } from "../hooks/useHealthStatus";
import { IconGithub, IconMark, IconSearch } from "./Icons";

const LABEL = { checking: "Checking API", online: "API connected", offline: "API unreachable" } as const;

const LINKS = [
  { to: "/", label: "Overview" },
  { to: "/experiments", label: "Experiments" },
  { to: "/regressions", label: "Regressions" },
  { to: "/query-builder", label: "Query builder" },
  { to: "/queries", label: "Workload" },
];

export function Nav({ onSearch }: { onSearch: () => void }) {
  const { pathname } = useLocation();
  const health = useHealthStatus();
  const [regressions, setRegressions] = useState(0);

  // Refresh on navigation so the count is right after a run finishes.
  useEffect(() => {
    api.listRegressions().then((r) => setRegressions(r.filter((x) => outcome(x) === "slower").length)).catch(() => setRegressions(0));
  }, [pathname]);

  return (
    <header className="topnav">
      <Link to="/" className="brand">
        <IconMark />
        planshift
      </Link>

      <nav className="nav-links" aria-label="Main">
        {LINKS.map((l) => (
          <NavLink key={l.to} to={l.to} end={l.to === "/"}
            className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
            {l.label}
            {l.to === "/regressions" && regressions > 0 && (
              <span className="nav-count" aria-label={`${regressions} regressed queries`}>{regressions}</span>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="nav-tools">
        <span className={`health${health === "offline" ? " is-offline" : ""}`} title="Live status of GET /api/health, checked every 15s">
          <span className={`health-dot ${health}`} />
          <span className="health-label">{LABEL[health]}</span>
        </span>
        <button className="icon-btn" onClick={onSearch} aria-label="Search" title="Search (Ctrl or ⌘ K)">
          <IconSearch />
        </button>
        <a className="icon-btn" href="https://github.com/Vrinda072/planshift" target="_blank" rel="noreferrer"
          aria-label="Source on GitHub" title="Source on GitHub">
          <IconGithub />
        </a>
        <Link to="/query-builder" className="btn btn-primary">New experiment</Link>
      </div>
    </header>
  );
}
