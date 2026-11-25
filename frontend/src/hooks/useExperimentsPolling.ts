import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { Experiment } from "../api/types";
import { isUnfinished } from "../lib/status";

/** Follows several experiments until all of them have finished. `key` is the comma-separated list of ids. */
export function useExperimentsPolling(key: string, intervalMs = 1000) {
  const [state, setState] = useState<{ key: string; experiments: Experiment[] } | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const ids = key.split(",").map(Number).filter(Number.isInteger).filter((id) => id > 0);
    if (ids.length === 0) return;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const poll = async () => {
      try {
        const experiments = await Promise.all(ids.map((id) => api.getExperiment(id)));
        if (cancelled) return;
        setState({ key, experiments });
        setError(null);
        if (experiments.some((e) => isUnfinished(e.status))) timer = setTimeout(poll, intervalMs);
      } catch (e) {
        if (!cancelled) setError((e as Error).message);
      }
    };

    poll();
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [key, intervalMs]);

  // Results for a previous set of ids are never shown against the current one.
  return { experiments: state?.key === key ? state.experiments : null, error };
}
