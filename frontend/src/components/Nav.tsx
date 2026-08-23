import { NavLink } from "react-router-dom";

export function Nav() {
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
    </nav>
  );
}
