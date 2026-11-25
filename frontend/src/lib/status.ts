import type { ExperimentStatus } from "../api/types";

export const STATUS_LABEL: Record<ExperimentStatus, string> = {
  QUEUED: "Queued",
  RUNNING: "Running",
  COMPLETED: "Completed",
  FAILED: "Failed",
};

/** Still waiting or in progress, so worth polling again. */
export function isUnfinished(status: ExperimentStatus): boolean {
  return status === "QUEUED" || status === "RUNNING";
}
