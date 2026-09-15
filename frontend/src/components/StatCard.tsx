import type { ReactNode } from "react";

export function StatCard({
  label,
  value,
  tone = "neutral",
  hint,
}: {
  label: string;
  value: ReactNode;
  tone?: "positive" | "negative" | "neutral";
  hint?: string;
}) {
  return (
    <div className={`stat-card stat-card-${tone}`}>
      <div className="stat-card-label">{label}</div>
      <div className="stat-card-value">{value}</div>
      {hint && <div className="stat-card-hint">{hint}</div>}
    </div>
  );
}
