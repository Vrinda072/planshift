import type { MeasurementMode } from "../api/types";

export const RUN_ORDER_LABEL: Record<MeasurementMode, string> = {
  ALTERNATING: "Taking turns",
  BLOCKS: "In blocks",
};
