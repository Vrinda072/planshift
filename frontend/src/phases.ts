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
  PENDING: "Queued",
  DROPPING_INDEX: "Dropping the index",
  WARMING_UP_BASELINE: "Warming up cache (baseline)",
  MEASURING_BASELINE: "Measuring baseline",
  ADDING_INDEX: "Adding the index",
  WARMING_UP_CANDIDATE: "Warming up cache (candidate)",
  MEASURING_CANDIDATE: "Measuring candidate",
  COMPARING_RESULTS: "Comparing & saving results",
  DONE: "Done",
};

export const PHASE_EXPLANATION: Record<ExperimentPhase, string> = {
  PENDING: "The experiment has been created and is about to start.",
  DROPPING_INDEX:
    "Removing idx_orders_customer_id if it exists, so the baseline measurement reflects a database with no index on this column.",
  WARMING_UP_BASELINE:
    "Running the full workload once, untimed, so Postgres's memory cache is warm before real measurement begins. Without this, whichever phase runs first would look artificially slow.",
  MEASURING_BASELINE:
    "Running all 10 queries, 5 times each, and capturing a real EXPLAIN ANALYZE execution plan for each — this is the \"before\" state.",
  ADDING_INDEX: "Running CREATE INDEX on orders.customer_id — this is the database change being tested.",
  WARMING_UP_CANDIDATE: "Warming the cache again for the candidate configuration, for the same reason as before.",
  MEASURING_CANDIDATE:
    "Running all 10 queries again, 5 times each, with the index in place — this is the \"after\" state.",
  COMPARING_RESULTS:
    "Computing percentage change per query, classifying each as improved/unchanged/regressed, and diffing the before/after execution plans.",
  DONE: "Finished.",
};
