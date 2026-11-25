import type { MeasurementMode } from "../api/types";
import { RUN_ORDER_LABEL } from "../lib/runOrder";

export const RUN_ORDER_HELP =
  "Taking turns: runs with and without the index alternate, which reports much less noise but briefly blocks other readers of the table. In blocks: some runs without, all runs with, then the rest without.";

export function RunOrderSelect({
  value,
  onChange,
  disabled,
}: {
  value: MeasurementMode;
  onChange: (value: MeasurementMode) => void;
  disabled?: boolean;
}) {
  return (
    <select
      aria-label="Run order"
      title={RUN_ORDER_HELP}
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value as MeasurementMode)}
    >
      {(Object.keys(RUN_ORDER_LABEL) as MeasurementMode[]).map((mode) => (
        <option key={mode} value={mode}>
          {RUN_ORDER_LABEL[mode]}
        </option>
      ))}
    </select>
  );
}
