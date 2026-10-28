import { useEffect, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { api } from "../api/client";
import { IconFlask, IconGithub, IconList, IconMark, IconOverview, IconSliders, IconTrend } from "./Icons";

const linkClass = ({ isActive }: { isActive: boolean }) => `nav-link${isActive ? " active" : ""}`;

export function Nav() {
  const { pathname } = useLocation();
  const [regressions, setRegressions] = useState(0);

  // Refresh on navigation so the count is right after a run finishes.
  useEffect(() => {
    api.listRegressions().then((r) => setRegressions(r.length)).catch(() => setRegressions(0));
  }, [pathname]);

  return (
    <nav className="sidebar">
      <div className="brand">
        <IconMark />
        planshift
      </div>

      <div className="nav-group">
        <div className="nav-group-label">Workspace</div>
        <NavLink to="/" end className={linkClass}>
          <IconOverview />
          Overview
        </NavLink>
        <NavLink to="/experiments" className={linkClass}>
          <IconFlask />
          Experiments
        </NavLink>
        <NavLink to="/regressions" className={linkClass}>
          <IconTrend />
          Regressions
          {regressions > 0 && <span className="nav-count" aria-label={`${regressions} regressed queries`}>{regressions}</span>}
        </NavLink>
      </div>

      <div className="nav-group">
        <div className="nav-group-label">Build</div>
        <NavLink to="/query-builder" className={linkClass}>
          <IconSliders />
          Query builder
        </NavLink>
        <NavLink to="/queries" className={linkClass}>
          <IconList />
          Workload
        </NavLink>
      </div>

      <div className="sidebar-foot">
        <a href="https://github.com/Vrinda072/planshift" target="_blank" rel="noreferrer">
          <IconGithub />
          Source
        </a>
      </div>
    </nav>
  );
}
