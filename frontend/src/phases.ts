import type { ExperimentPhase } from "./api/types";

export const PHASE_ORDER: ExperimentPhase[] = [
  "PENDING",
  "DROPPING_INDEX",
  "WARMING_UP_BASELINE",
  "MEASURING_BASELINE",
  "ADDING_INDEX",
  "WARMING_UP_CANDIDATE",
  "MEASURING_CANDIDATE",
  "COMPARING_RESULTS",
  "DONE",
];

export const PHASE_LABEL: Record<ExperimentPhase, string> = {
  PENDING: "Starting",
  DROPPING_INDEX: "Preparing the baseline",
  WARMING_UP_BASELINE: "Warming up cache (baseline)",
  MEASURING_BASELINE: "Measuring baseline",
  ADDING_INDEX: "Creating the index",
  WARMING_UP_CANDIDATE: "Warming up cache (candidate)",
  MEASURING_CANDIDATE: "Measuring candidate",
  COMPARING_RESULTS: "Comparing & saving results",
  DONE: "Done",
};

export const PHASE_EXPLANATION: Record<ExperimentPhase, string> = {
  PENDING: "The experiment has been created and is about to start.",
  DROPPING_INDEX:
    "Making sure no temporary index from an interrupted run is still there. Indexes that already existed are left in place.",
  WARMING_UP_BASELINE:
    "Running the full workload once, untimed, so Postgres's memory cache is warm before real measurement begins. Without this, whichever phase runs first would look artificially slow.",
  MEASURING_BASELINE:
    "Timing each query repeatedly, then capturing a real EXPLAIN ANALYZE plan for it. This is the \"before\" state.",
  ADDING_INDEX: "Running CREATE INDEX on the chosen columns. This is the change being tested.",
  WARMING_UP_CANDIDATE: "Warming the cache again for the candidate configuration, for the same reason as before.",
  MEASURING_CANDIDATE:
    "Timing the same queries again with the index in place. This is the \"after\" state.",
  COMPARING_RESULTS:
    "Computing the change per query, comparing the before and after plans, and removing the temporary index.",
  DONE: "Finished.",
};
