import { BrowserRouter, Route, Routes, useLocation } from "react-router-dom";
import { Nav } from "./components/Nav";
import { Overview } from "./pages/Overview";
import { Experiments } from "./pages/Experiments";
import { ExperimentResult } from "./pages/ExperimentResult";
import { Queries } from "./pages/Queries";
import { Regressions } from "./pages/Regressions";
import { QueryBuilder } from "./pages/QueryBuilder";

function AnimatedRoutes() {
  const location = useLocation();
  // Keying on pathname remounts the page subtree on navigation, which
  // restarts the .page-enter CSS animation -- a lightweight fade/slide
  // instead of the page just snapping into place.
  return (
    <div className="page-enter" key={location.pathname}>
      <Routes location={location}>
        <Route path="/" element={<Overview />} />
        <Route path="/experiments" element={<Experiments />} />
        <Route path="/experiments/:id" element={<ExperimentResult />} />
        <Route path="/queries" element={<Queries />} />
        <Route path="/query-builder" element={<QueryBuilder />} />
        <Route path="/regressions" element={<Regressions />} />
      </Routes>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <div className="app-shell">
        <Nav />
        <main className="main">
          <AnimatedRoutes />
        </main>
      </div>
    </BrowserRouter>
  );
}
