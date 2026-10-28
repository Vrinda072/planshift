import { NavLink } from "react-router-dom";
import { IconFlask, IconGithub, IconList, IconMark, IconOverview, IconSliders, IconTrend } from "./Icons";

const linkClass = ({ isActive }: { isActive: boolean }) => `nav-link${isActive ? " active" : ""}`;

export function Nav() {
  return (
    <nav className="sidebar">
      <div className="brand">
        <span className="brand-mark">
          <IconMark />
        </span>
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
