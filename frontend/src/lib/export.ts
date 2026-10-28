import type { Experiment } from "../api/types";

function download(filename: string, mime: string, content: string) {
  const url = URL.createObjectURL(new Blob([content], { type: mime }));
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export function exportJson(experiment: Experiment) {
  download(`planshift-experiment-${experiment.experimentId}.json`, "application/json", JSON.stringify(experiment, null, 2));
}

const csvCell = (v: string | number) => `"${String(v).replace(/"/g, '""')}"`;

export function exportCsv(experiment: Experiment) {
  const rows = [
    ["query", "baseline_median_ms", "candidate_median_ms", "change_percent", "status"],
    ...experiment.queryResults.map((r) => [
      r.queryName,
      r.baselineMedianMs,
      r.candidateMedianMs,
      r.percentageChange,
      r.status,
    ]),
  ];
  download(`planshift-experiment-${experiment.experimentId}.csv`, "text/csv", rows.map((r) => r.map(csvCell).join(",")).join("\n"));
}
