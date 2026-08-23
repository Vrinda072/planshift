import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { Experiment } from "../api/types";

/**
 * Polls a real experiment's status while it's RUNNING. This reflects genuine
 * server-side progress (the backend updates current_phase as each real step
 * completes) rather than a simulated/fake progress indicator.
 */
export function useExperimentPolling(experimentId: number | null, intervalMs = 600) {
  const [experiment, setExperiment] = useState<Experiment | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (experimentId === null) {
      setExperiment(null);
      setError(null);
      return;
    }
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const poll = async () => {
      try {
        const result = await api.getExperiment(experimentId);
        if (cancelled) return;
        setExperiment(result);
        if (result.status === "RUNNING") {
          timer = setTimeout(poll, intervalMs);
        }
      } catch (e) {
        if (!cancelled) setError((e as Error).message);
      }
    };

    poll();
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [experimentId, intervalMs]);

  return { experiment, error };
}
