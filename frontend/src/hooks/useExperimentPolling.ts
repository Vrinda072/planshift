import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { Experiment } from "../api/types";

/**
 * Polls a real experiment's status while it's RUNNING. This reflects genuine
 * server-side progress (the backend updates current_phase as each real step
 * completes) rather than a simulated/fake progress indicator.
 *
 * Polling pauses whenever the tab is hidden and resumes with an immediate
 * poll on refocus -- a backgrounded tab has no one watching the progress
 * bar, so there's no reason to keep hitting the API every 600ms while it's
 * not visible. This is the same idea as useHealthStatus's pause/resume,
 * applied to the higher-frequency poll.
 */
export function useExperimentPolling(experimentId: number | null, intervalMs = 600) {
  const [experiment, setExperiment] = useState<Experiment | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (experimentId === null) {
      return;
    }
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let latestStatus: Experiment["status"] | null = null;

    const scheduleNext = () => {
      if (cancelled || latestStatus !== "RUNNING") return;
      if (document.visibilityState === "visible") {
        timer = setTimeout(poll, intervalMs);
      }
      // else: stay paused -- onVisibilityChange below resumes it.
    };

    const poll = async () => {
      try {
        const result = await api.getExperiment(experimentId);
        if (cancelled) return;
        setExperiment(result);
        setError(null);
        latestStatus = result.status;
        scheduleNext();
      } catch (e) {
        if (!cancelled) setError((e as Error).message);
      }
    };

    const onVisibilityChange = () => {
      if (document.visibilityState === "visible" && latestStatus === "RUNNING") {
        if (timer) clearTimeout(timer);
        poll();
      }
    };

    poll();
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [experimentId, intervalMs]);

  // Derived during render rather than reset via an effect -- avoids a
  // synchronous setState inside the effect body for the "no id" case.
  if (experimentId === null) {
    return { experiment: null, error: null };
  }
  return { experiment, error };
}
