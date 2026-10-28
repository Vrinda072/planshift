import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { Experiment } from "../api/types";
import { formatChangeText } from "../format";

interface Item {
  id: string;
  group: string;
  label: string;
  hint?: string;
  run: () => void;
}

export function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const [experiments, setExperiments] = useState<Experiment[]>([]);

  useEffect(() => {
    if (!open) return;
    inputRef.current?.focus();
    api.listExperiments().then(setExperiments).catch(() => setExperiments([]));
  }, [open]);

  const items = useMemo<Item[]>(() => {
    const go = (to: string) => () => {
      navigate(to);
      onClose();
    };
    const pages: Item[] = [
      { id: "p-overview", group: "Go to", label: "Overview", run: go("/") },
      { id: "p-experiments", group: "Go to", label: "Experiments", run: go("/experiments") },
      { id: "p-regressions", group: "Go to", label: "Regressions", run: go("/regressions") },
      { id: "p-builder", group: "Go to", label: "Query builder", run: go("/query-builder") },
      { id: "p-workload", group: "Go to", label: "Workload", run: go("/queries") },
    ];
    const actions: Item[] = [
      {
        id: "a-run",
        group: "Actions",
        label: "Run the built-in experiment",
        hint: "5 runs per query, 20% threshold",
        run: async () => {
          onClose();
          const started = await api.startExperiment(5, 0.2);
          navigate(`/experiments/${started.experimentId}`);
        },
      },
    ];
    const recent: Item[] = experiments.slice(0, 8).map((e) => ({
      id: `e-${e.experimentId}`,
      group: "Experiments",
      label: `#${e.experimentId}  ${e.targetTable ?? "orders"}.${e.targetColumn ?? "customer_id"}`,
      hint:
        e.status === "COMPLETED" && e.overallPercentageChange !== null
          ? formatChangeText(e.overallPercentageChange)
          : e.status.toLowerCase(),
      run: go(`/experiments/${e.experimentId}`),
    }));
    return [...actions, ...pages, ...recent];
  }, [experiments, navigate, onClose]);

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return items;
    return items.filter((i) => `${i.group} ${i.label}`.toLowerCase().includes(q));
  }, [items, query]);

  // Keep the highlight in range when the list shrinks, without a state update in an effect.
  const activeIndex = Math.min(active, Math.max(results.length - 1, 0));

  if (!open) return null;

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") {
      onClose();
    } else if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive(Math.min(activeIndex + 1, results.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive(Math.max(activeIndex - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      results[activeIndex]?.run();
    }
  };

  let lastGroup = "";

  return (
    <div className="palette-backdrop" onMouseDown={onClose}>
      <div
        className="palette"
        role="dialog"
        aria-modal="true"
        aria-label="Search"
        onMouseDown={(e) => e.stopPropagation()}
        onKeyDown={onKeyDown}
      >
        <input
          ref={inputRef}
          className="palette-input"
          placeholder="Search experiments, pages, actions"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setActive(0);
          }}
          aria-label="Search"
        />
        <div className="palette-list" role="listbox">
          {results.length === 0 && <div className="palette-empty">Nothing matches "{query}".</div>}
          {results.map((item, i) => {
            const showGroup = item.group !== lastGroup;
            lastGroup = item.group;
            return (
              <div key={item.id}>
                {showGroup && <div className="palette-group">{item.group}</div>}
                <div
                  role="option"
                  aria-selected={i === activeIndex}
                  className={`palette-item${i === activeIndex ? " active" : ""}`}
                  onMouseEnter={() => setActive(i)}
                  onClick={item.run}
                >
                  <span>{item.label}</span>
                  {item.hint && <span className="palette-hint">{item.hint}</span>}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
