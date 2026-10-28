import { useEffect, useState } from "react";
import { BrowserRouter, Route, Routes, useLocation } from "react-router-dom";
import { Nav } from "./components/Nav";
import { CommandPalette } from "./components/CommandPalette";
import { Topbar } from "./components/Topbar";
import { Compare } from "./pages/Compare";
import { NotFound } from "./pages/NotFound";
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
        <Route path="/compare" element={<Compare />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </div>
  );
}

export default function App() {
  const [paletteOpen, setPaletteOpen] = useState(false);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setPaletteOpen((o) => !o);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <BrowserRouter>
      <div className="app">
        <Nav />
        <div className="main-col">
          <Topbar onSearch={() => setPaletteOpen(true)} />
          <main className="content">
            <AnimatedRoutes />
          </main>
        </div>
      </div>
      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
    </BrowserRouter>
  );
}
