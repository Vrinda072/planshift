import { useEffect, useState } from "react";
import { api } from "../api/client";

export type HealthState = "checking" | "online" | "offline";

/**
 * Polls the backend's real /api/health endpoint (which itself checks the
 * database connection, not just that the process is up) so the sidebar
 * shows a genuine live status rather than a static "connected" label.
 * Polling pauses while the tab is in the background and resumes
 * immediately on refocus, so an idle background tab doesn't keep hitting
 * the API every few seconds for no one to see.
 */
export function useHealthStatus(intervalMs = 15000): HealthState {
  const [state, setState] = useState<HealthState>("checking");

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const check = async () => {
      try {
        const result = await api.health();
        if (!cancelled) setState(result.status === "ok" ? "online" : "offline");
      } catch {
        if (!cancelled) setState("offline");
      } finally {
        if (!cancelled && document.visibilityState === "visible") {
          timer = setTimeout(check, intervalMs);
        }
      }
    };

    const onVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        if (timer) clearTimeout(timer);
        check();
      }
    };

    check();
    document.addEventListener("visibilitychange", onVisibilityChange);
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
      document.removeEventListener("visibilitychange", onVisibilityChange);
    };
  }, [intervalMs]);

  return state;
}
