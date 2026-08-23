import type { Experiment, ExperimentQueryResult, QuerySpec, TableInfo, WorkloadQuery } from "./types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({ error: response.statusText }));
    throw new Error(body.error ?? `Request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export interface HealthStatus {
  status: string;
  database?: string;
}

export interface ImportResult {
  tableName: string;
  columnNames: string[];
  rowCount: number;
}

export const api = {
  health: () => request<HealthStatus>("/api/health"),
  listQueries: () => request<WorkloadQuery[]>("/api/queries"),
  listExperiments: () => request<Experiment[]>("/api/experiments"),
  getExperiment: (id: number) => request<Experiment>(`/api/experiments/${id}`),
  // Returns immediately with the experiment in RUNNING state -- the real work
  // happens on the server in the background. Poll getExperiment(id) for progress.
  startExperiment: (repetitions: number, thresholdFraction: number) =>
    request<Experiment>("/api/experiments", {
      method: "POST",
      body: JSON.stringify({ repetitions, thresholdFraction }),
    }),
  listRegressions: () => request<ExperimentQueryResult[]>("/api/regressions"),
  listTables: () => request<TableInfo[]>("/api/schema/tables"),
  previewQuery: (spec: QuerySpec) =>
    request<WorkloadQuery>("/api/query-builder/preview", {
      method: "POST",
      body: JSON.stringify(spec),
    }),
  runQueryBuilderExperiment: (spec: QuerySpec, indexColumn: string, repetitions: number, thresholdFraction: number) =>
    request<Experiment>("/api/query-builder/run", {
      method: "POST",
      body: JSON.stringify({ spec, indexColumn, repetitions, thresholdFraction }),
    }),
  importCsv: async (file: File, tableName: string): Promise<ImportResult> => {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("tableName", tableName);
    // No Content-Type header here on purpose -- the browser sets the correct
    // multipart/form-data boundary itself when the body is a FormData.
    const response = await fetch(`${BASE_URL}/api/datasets/import`, { method: "POST", body: formData });
    if (!response.ok) {
      const body = await response.json().catch(() => ({ error: response.statusText }));
      throw new Error(body.error ?? `Request failed: ${response.status}`);
    }
    return response.json() as Promise<ImportResult>;
  },
};
