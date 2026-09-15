import { NavLink } from "react-router-dom";
import { useHealthStatus } from "../hooks/useHealthStatus";

const HEALTH_LABEL: Record<ReturnType<typeof useHealthStatus>, string> = {
  checking: "Checking...",
  online: "API connected",
  offline: "API unreachable",
};

export function Nav() {
  const health = useHealthStatus();

  return (
    <nav className="sidebar">
      <div className="brand">PLANSHIFT</div>
      <div className="nav-links">
        <NavLink to="/" end className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
          Overview
        </NavLink>
        <NavLink to="/experiments" className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
          Experiments
        </NavLink>
        <NavLink to="/queries" className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
          Queries
        </NavLink>
        <NavLink to="/query-builder" className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
          Query Builder
        </NavLink>
        <NavLink to="/regressions" className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
          Regressions
        </NavLink>
      </div>
      <div className="nav-footer" title="Live status of GET /api/health, checked every 15s">
        <span className={`health-dot ${health}`} />
        {HEALTH_LABEL[health]}
      </div>
    </nav>
  );
}
