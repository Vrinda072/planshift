export function formatChangeSymbol(percentageChange: number): string {
  return percentageChange < 0 ? "↓" : percentageChange > 0 ? "↑" : "→";
}

export function formatChangeText(percentageChange: number): string {
  return `${formatChangeSymbol(percentageChange)} ${Math.abs(percentageChange).toFixed(1)}%`;
}

export function changeClassName(percentageChange: number): string {
  if (percentageChange < 0) return "change-negative";
  if (percentageChange > 0) return "change-positive";
  return "change-neutral";
}

export function formatMs(ms: number): string {
  return `${ms.toFixed(ms < 10 ? 3 : 1)}ms`;
}

export function formatBytes(bytes: number): string {
  return bytes < 1024 * 1024 ? `${Math.round(bytes / 1024)} kB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}
