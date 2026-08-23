import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Nav } from "./components/Nav";
import { Overview } from "./pages/Overview";
import { Experiments } from "./pages/Experiments";
import { ExperimentResult } from "./pages/ExperimentResult";
import { Queries } from "./pages/Queries";
import { Regressions } from "./pages/Regressions";
import { QueryBuilder } from "./pages/QueryBuilder";

export default function App() {
  return (
    <BrowserRouter>
      <div className="app-shell">
        <Nav />
        <main className="main">
          <Routes>
            <Route path="/" element={<Overview />} />
            <Route path="/experiments" element={<Experiments />} />
            <Route path="/experiments/:id" element={<ExperimentResult />} />
            <Route path="/queries" element={<Queries />} />
            <Route path="/query-builder" element={<QueryBuilder />} />
            <Route path="/regressions" element={<Regressions />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}
